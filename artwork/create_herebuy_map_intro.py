"""Build the separate 210-frame HereBuy map intro in Blender 5.3."""

import bpy
import math
from pathlib import Path
from mathutils import Vector

ROOT = Path(__file__).resolve().parent
OUT = ROOT / "HereBuy_MapIntro_PNG"
OUT.mkdir(exist_ok=True)

bpy.ops.object.select_all(action="SELECT")
bpy.ops.object.delete(use_global=False)
for material in list(bpy.data.materials):
    if material.users == 0:
        bpy.data.materials.remove(material)

scene = bpy.context.scene
scene.frame_start = 1
scene.frame_end = 210
scene.render.fps = 60
scene.render.fps_base = 1
scene.render.resolution_x = 1080
scene.render.resolution_y = 1920
scene.render.resolution_percentage = 100
scene.render.engine = "BLENDER_EEVEE"
scene.render.image_settings.file_format = "PNG"
scene.render.image_settings.color_mode = "RGBA"
scene.render.film_transparent = False
scene.render.filepath = str(OUT / "frame_")
scene.render.image_settings.color_depth = "8"
scene.render.use_file_extension = True
scene.render.resolution_percentage = 100
scene.render.engine = "BLENDER_EEVEE"
scene.render.image_settings.compression = 20
scene.world.color = (0.62, 0.78, 0.61)
try:
    scene.eevee.taa_render_samples = 16
except AttributeError:
    pass


def rgba(hex_color, alpha=1.0):
    value = hex_color.lstrip("#")
    return tuple(int(value[i:i+2], 16) / 255 for i in (0, 2, 4)) + (alpha,)


def mat(name, hex_color, alpha=1.0):
    material = bpy.data.materials.new(name)
    material.diffuse_color = rgba(hex_color, alpha)
    material.use_nodes = True
    bsdf = next(node for node in material.node_tree.nodes if node.type == "BSDF_PRINCIPLED")
    bsdf.inputs["Base Color"].default_value = rgba(hex_color)
    bsdf.inputs["Roughness"].default_value = 0.9
    bsdf.inputs["Metallic"].default_value = 0.0
    bsdf.inputs["Alpha"].default_value = alpha
    material.surface_render_method = "DITHERED"
    return material


SAGE = mat("01 sage map base #CFE0CC", "#CFE0CC")
sage_nodes = SAGE.node_tree.nodes
sage_nodes.clear()
sage_output = sage_nodes.new("ShaderNodeOutputMaterial")
sage_emission = sage_nodes.new("ShaderNodeEmission")
sage_emission.inputs["Color"].default_value = rgba("#CFE0CC")
sage_emission.inputs["Strength"].default_value = 1.0
SAGE.node_tree.links.new(sage_emission.outputs[0], sage_output.inputs["Surface"])
EMERALD = mat("02 emerald #0F7B5F", "#0F7B5F")
DARK = mat("03 deep emerald", "#095D48")
GOLD = mat("04 warm gold #E3B341", "#E3B341")
CREAM = mat("05 cream #FFF8E7", "#FFF8E7")
PARK = mat("06 park mint", "#A6CFAC")
BLOCK = mat("07 neighborhood block", "#E4EDD9")
RING_MATS = [mat(f"08 ping {i} alpha", "#0F7B5F", 0.0) for i in range(2)]
MAP_MATS = [mat("09 map road fade", "#FFF8E7"), mat("10 map park fade", "#A6CFAC"), mat("11 map block fade", "#E4EDD9")]
HALO = mat("12 emblem gold halo", "#E3B341", 0.0)
SWEEP = mat("13 cream light sweep", "#FFF8E7", 0.0)


def alpha_key(material, frame, value):
    node = next(node for node in material.node_tree.nodes if node.type == "BSDF_PRINCIPLED")
    socket = node.inputs["Alpha"]
    socket.default_value = value
    socket.keyframe_insert(data_path="default_value", frame=frame)


def parent_to(obj, parent):
    obj.parent = parent
    obj.matrix_parent_inverse.identity()
    return obj


def empty(name, location=(0, 0, 0), parent=None):
    obj = bpy.data.objects.new(name, None)
    scene.collection.objects.link(obj)
    obj.location = location
    if parent:
        parent_to(obj, parent)
    return obj


def cube(name, location, scale, material, bevel=0.08, parent=None):
    bpy.ops.mesh.primitive_cube_add(size=1, location=location)
    obj = bpy.context.object
    obj.name = name
    obj.dimensions = scale
    bpy.ops.object.transform_apply(location=False, rotation=False, scale=True)
    if bevel:
        modifier = obj.modifiers.new("Soft rounded edges", "BEVEL")
        modifier.width = bevel
        modifier.segments = 3
        obj.modifiers.new("Weighted normals", "WEIGHTED_NORMAL")
    obj.data.materials.append(material)
    if parent:
        parent_to(obj, parent)
    return obj


def sphere(name, location, radius, material, parent=None, segments=20):
    bpy.ops.mesh.primitive_uv_sphere_add(segments=segments, ring_count=12, radius=radius, location=location)
    obj = bpy.context.object
    obj.name = name
    bpy.ops.object.shade_smooth()
    obj.data.materials.append(material)
    if parent:
        parent_to(obj, parent)
    return obj


def torus(name, radius, thickness, material, parent=None):
    bpy.ops.mesh.primitive_torus_add(major_radius=radius, minor_radius=thickness, major_segments=48, minor_segments=8)
    obj = bpy.context.object
    obj.name = name
    obj.data.materials.append(material)
    if parent:
        parent_to(obj, parent)
    return obj


def key(obj, path, frame, value):
    setattr(obj, path, value)
    obj.keyframe_insert(data_path=path, frame=frame)


def scale_keys(obj, keys):
    for frame, value in keys:
        key(obj, "scale", frame, (value, value, value))


def make_eased():
    for action in bpy.data.actions:
        for layer in action.layers:
            for strip in layer.strips:
                if strip.type != "KEYFRAME":
                    continue
                for bag in strip.channelbags:
                    for curve in bag.fcurves:
                        for point in curve.keyframe_points:
                            point.interpolation = "BEZIER"
                            point.handle_left_type = "AUTO_CLAMPED"
                            point.handle_right_type = "AUTO_CLAMPED"


# A broad sage floor and shallow raised map pieces create soft contact shadows.
cube("Sage full-screen map base", (0, 0, -0.16), (12.0, 19.0, 0.24), SAGE, 0)
map_root = empty("MAP | streets parks blocks")
scale_keys(map_root, [(1, 1), (128, 1), (154, 0.001), (210, 0.001)])


def road(name, coords, thickness):
    curve = bpy.data.curves.new(name, "CURVE")
    curve.dimensions = "3D"
    curve.resolution_u = 18
    curve.bevel_depth = thickness
    curve.bevel_resolution = 3
    spline = curve.splines.new("BEZIER")
    spline.bezier_points.add(len(coords) - 1)
    for point, xy in zip(spline.bezier_points, coords):
        point.co = (xy[0], xy[1], 0.035)
        point.handle_left_type = "AUTO"
        point.handle_right_type = "AUTO"
    obj = bpy.data.objects.new(name, curve)
    scene.collection.objects.link(obj)
    obj.data.materials.append(MAP_MATS[0])
    parent_to(obj, map_root)
    for frame, amount in ((1, 0), (8, 0.12), (30, 1), (135, 1), (156, 1), (210, 0)):
        curve.bevel_factor_end = amount
        curve.keyframe_insert(data_path="bevel_factor_end", frame=frame)


road("Cream avenue central", [(0, 0), (-0.8, 2.5), (-0.2, 5.5)], 0.17)
road("Cream avenue south", [(0, 0), (0.35, -2.5), (1.15, -5.7)], 0.15)
road("Cream street west upper", [(0, 0), (-1.5, 1.7), (-3.6, 2.4)], 0.11)
road("Cream street east upper", [(0, 0), (1.4, 1.8), (3.6, 3.1)], 0.11)
road("Cream street west lower", [(0, 0), (-1.4, -1.7), (-3.7, -2.8)], 0.11)
road("Cream street east lower", [(0, 0), (1.5, -1.9), (3.8, -2.4)], 0.11)
road("Cream cross street", [(-3.6, 4.3), (-0.3, 4.1), (3.6, 4.5)], 0.09)

blocks = [
    (-2.35, 4.35, 0.72, 0.70, 0.10), (1.45, 4.3, 0.84, 0.68, -0.08),
    (-2.2, 2.9, 0.76, 0.54, 0.12), (2.3, 2.2, 0.65, 0.82, 0.15),
    (-2.55, 0.45, 0.92, 0.62, -0.09), (2.4, 0.25, 0.75, 0.67, 0.08),
    (-2.3, -1.5, 0.82, 0.60, 0.12), (2.2, -1.45, 0.88, 0.58, -0.10),
    (-1.9, -3.55, 0.78, 0.75, -0.06), (2.25, -3.65, 0.76, 0.68, 0.08),
    (-0.6, -4.55, 0.72, 0.55, 0.1), (2.7, 5.3, 0.7, 0.45, -0.1),
]
for index, (x, y, w, h, angle) in enumerate(blocks):
    park = index in (2, 6, 9)
    obj = cube(f"{'Park' if park else 'Rounded block'} {index + 1:02d}", (x, y, 0.08), (w, h, 0.13), MAP_MATS[1 if park else 2], 0.12, map_root)
    obj.rotation_euler.z = angle
    start = 5 + index * 2
    scale_keys(obj, [(1, 0.001), (start, 0.001), (start + 5, 1.14), (start + 9, 1), (135, 1), (210, 0.001)])
for material in MAP_MATS:
    for frame, value in ((1, 1), (127, 1), (154, 0), (210, 1)):
        alpha_key(material, frame, value)

# Two soft radius pings expand from the gold location dot.
dot = empty("GOLD LOCATION DOT | morph source", (0, 0, 0.25))
sphere("Gold dot", (0, 0, 0), 0.20, GOLD, dot)
scale_keys(dot, [(1, 0.001), (36, 0.001), (47, 1.24), (52, 0.88), (58, 1), (135, 1), (148, 1.4), (156, 0.001), (210, 0.001)])
for frame, z in ((1, 0.8), (36, 0.8), (47, 0.18), (52, 0.44), (58, 0.25), (156, 0.25), (210, 0.8)):
    key(dot, "location", frame, (0, 0, z))
for index, start in enumerate((43, 60)):
    ring = empty(f"EMERALD PING {index + 1}", (0, 0, 0.21))
    torus(f"Ping halo {index + 1}", 0.50, 0.024, RING_MATS[index], ring)
    scale_keys(ring, [(1, 0.001), (start, 0.001), (start + 8, 1.15), (start + 22, 5.2), (start + 27, 0.001), (210, 0.001)])
    for frame, value in ((1, 0), (start, 0), (start + 5, 0.58), (start + 22, 0), (210, 0)):
        alpha_key(RING_MATS[index], frame, value)


def icon(kind, group):
    if kind == "plant":
        cube("Plant stem", (0, 0.07, 0.235), (0.025, 0.16, 0.03), DARK, 0.005, group)
        sphere("Plant left leaf", (-0.065, 0.13, 0.24), 0.06, EMERALD, group)
        sphere("Plant right leaf", (0.065, 0.18, 0.24), 0.055, EMERALD, group)
    elif kind == "lamp":
        cube("Lamp stem", (0, 0.05, 0.24), (0.025, 0.16, 0.03), DARK, 0.005, group)
        shade = cube("Lamp shade", (0, 0.17, 0.25), (0.18, 0.07, 0.04), GOLD, 0.015, group)
        cube("Lamp base", (0, -0.04, 0.24), (0.14, 0.025, 0.03), DARK, 0.005, group)
    elif kind == "book":
        cube("Book cover", (0, 0.07, 0.24), (0.17, 0.19, 0.035), GOLD, 0.018, group)
        cube("Book spine", (-0.06, 0.07, 0.266), (0.02, 0.17, 0.01), DARK, 0.003, group)
    else:
        cube("Chair seat", (0, 0.03, 0.24), (0.18, 0.045, 0.035), GOLD, 0.008, group)
        cube("Chair back", (0, 0.13, 0.24), (0.18, 0.14, 0.035), GOLD, 0.01, group)
        for x in (-0.065, 0.065):
            cube("Chair leg", (x, -0.055, 0.24), (0.025, 0.1, 0.03), DARK, 0.005, group)


pin_data = [
    (-2.15, 3.1, "plant"), (1.95, 3.4, "lamp"), (-2.45, 0.55, "book"),
    (2.25, -0.35, "chair"), (-1.8, -3.15, "plant"), (1.9, -3.1, "book"),
]
for index, (x, y, kind) in enumerate(pin_data):
    group = empty(f"LISTING PIN {index + 1:02d} {kind}", (x, y, 0.21))
    sphere("Emerald pin head", (0, 0.1, 0), 0.22, EMERALD, group)
    cone_vertices = [(-0.13, -0.02, 0.005), (0.13, -0.02, 0.005), (0, -0.33, 0.005),
                     (-0.13, -0.02, 0.12), (0.13, -0.02, 0.12), (0, -0.33, 0.12)]
    mesh = bpy.data.meshes.new("Rounded pin point mesh")
    mesh.from_pydata(cone_vertices, [], [(0, 1, 2), (3, 5, 4), (0, 3, 4, 1), (1, 4, 5, 2), (2, 5, 3, 0)])
    mesh.update()
    tip = bpy.data.objects.new("Emerald pin point", mesh)
    scene.collection.objects.link(tip)
    tip.data.materials.append(EMERALD)
    parent_to(tip, group)
    sphere("Cream pin icon disc", (0, 0.1, 0.145), 0.15, CREAM, group).scale.z = 0.25
    icon(kind, group)
    start = 73 + index * 7
    scale_keys(group, [(1, 0.001), (start, 0.001), (start + 6, 1.25), (start + 10, 0.92), (start + 14, 1), (120, 1), (139 + index * 2, 0.001), (210, 0.001)])
    # The midpoint rises and curls around the central gold dot.
    mid = 126 + index * 3
    end = 142 + index * 2
    for frame, loc in ((1, (x, y, 0.21)), (120, (x, y, 0.21)),
                       (mid, (x * 0.65 - y * 0.12, y * 0.65 + x * 0.12, 0.82)),
                       (end, (0, 0, 0.28)), (210, (x, y, 0.21))):
        key(group, "location", frame, loc)

# A compact house emblem grows from the merged dot; a gold halo and cream sweep finish it.
emblem = empty("HEREBUY HOUSE EMBLEM", (0, 0.65, 0.35))
cube("Emerald house body", (0, -0.03, 0), (1.05, 0.85, 0.15), EMERALD, 0.13, emblem)
for x, angle in ((-0.28, -math.pi / 4), (0.28, math.pi / 4)):
    roof = cube("Gold roof stroke", (x, 0.54, 0.05), (0.66, 0.13, 0.19), GOLD, 0.055, emblem)
    roof.rotation_euler.z = angle
cube("Cream door", (0, -0.27, 0.095), (0.21, 0.36, 0.035), CREAM, 0.035, emblem)
for x in (-0.3, 0.3):
    cube("Cream window", (x, 0.02, 0.09), (0.16, 0.16, 0.03), CREAM, 0.035, emblem)
scale_keys(emblem, [(1, 0.001), (140, 0.001), (151, 2.42), (156, 2.03), (164, 2.1), (193, 2.1), (204, 0.001), (210, 0.001)])
halo = empty("GOLD EMBLEM HALO", (0, 0.65, 0.24))
torus("Gold halo ring", 0.88, 0.065, HALO, halo)
scale_keys(halo, [(1, 0.001), (150, 0.001), (160, 1.55), (176, 1.8), (196, 1.55), (207, 0.001), (210, 0.001)])
for frame, value in ((1, 0), (150, 0), (162, 0.38), (185, 0.2), (202, 0.15), (210, 0)):
    alpha_key(HALO, frame, value)
sweep = empty("ONE CREAM LIGHT SWEEP", (-0.9, 0.65, 0.53))
bar = cube("Soft sweep bar", (0, 0, 0), (0.13, 1.5, 0.015), SWEEP, 0.045, sweep)
bar.rotation_euler.z = -0.35
for frame, loc in ((1, (-0.9, 0.65, 0.53)), (153, (-0.9, 0.65, 0.53)), (165, (0.95, 0.65, 0.53)), (210, (0.95, 0.65, 0.53))):
    key(sweep, "location", frame, loc)
for frame, value in ((1, 0), (153, 0), (156, 0.7), (162, 0.7), (165, 0), (210, 0)):
    alpha_key(SWEEP, frame, value)

# Two part wordmark moves inward below the house.
font_path = Path("C:/Windows/Fonts/seguisb.ttf")
font = bpy.data.fonts.load(str(font_path)) if font_path.exists() else None
for word, x, material, incoming in (("Here", -1.3, EMERALD, -4.2), ("Buy", 1.3, GOLD, 4.2)):
    curve = bpy.data.curves.new(f"{word} font", "FONT")
    curve.body = word
    curve.size = 0.76
    curve.align_x = "CENTER"
    curve.extrude = 0.015
    curve.bevel_depth = 0.005
    if font:
        curve.font = font
    obj = bpy.data.objects.new(f"WORDMARK {word}", curve)
    scene.collection.objects.link(obj)
    obj.data.materials.append(material)
    for frame, loc in ((1, (incoming, -1.4, 0.33)), (156, (incoming, -1.4, 0.33)),
                       (177, (x + (0.10 if word == "Here" else -0.10), -1.4, 0.33)),
                       (185, (x, -1.4, 0.33)), (194, (x, -1.4, 0.33)),
                       (205, (incoming, -1.4, 0.33)), (210, (incoming, -1.4, 0.33))):
        key(obj, "location", frame, loc)
    scale_keys(obj, [(1, 0.001), (156, 0.001), (175, 1.57), (185, 1.45), (194, 1.45), (205, 0.001), (210, 0.001)])

# Nearly top-down orthographic camera eases into a slight three-quarter tilt.
camera_data = bpy.data.cameras.new("Portrait orthographic camera")
camera = bpy.data.objects.new("Portrait camera | top down to three-quarter", camera_data)
scene.collection.objects.link(camera)
scene.camera = camera
camera_data.type = "ORTHO"
camera_data.ortho_scale = 13.6
for frame, angle in ((1, 0), (36, 0.13), (120, 0.16), (156, 0.08), (192, 0.04), (210, 0)):
    camera.location = (0, -13 * math.sin(angle), 15)
    camera.rotation_euler = (angle, 0, 0)
    camera.keyframe_insert(data_path="location", frame=frame)
    camera.keyframe_insert(data_path="rotation_euler", frame=frame)

world = bpy.data.worlds.new("Sage ambient world")
world.use_nodes = True
background = next(node for node in world.node_tree.nodes if node.type == "BACKGROUND")
background.inputs["Color"].default_value = rgba("#FFF8E7")
background.inputs["Strength"].default_value = 0.6
scene.world = world
light_data = bpy.data.lights.new("Large softbox", "AREA")
light = bpy.data.objects.new("Large softbox", light_data)
scene.collection.objects.link(light)
light.location = (-3.5, -4.5, 8)
light_data.energy = 360
light_data.shape = "DISK"
light_data.size = 8

make_eased()
scene.frame_set(1)
bpy.ops.wm.save_as_mainfile(filepath=str(ROOT / "HereBuyMapIntro_3_5s.blend"))
print("Built", len(scene.objects), "objects, frames", scene.frame_start, scene.frame_end, "at", scene.render.fps, "fps")
