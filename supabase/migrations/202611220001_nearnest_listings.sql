-- NearNest's first backend slice: seller-managed listings and nearby discovery.
-- Run this migration in the Supabase SQL Editor for the new project.

create schema if not exists extensions;
create extension if not exists postgis with schema extensions;

create table if not exists public.listings (
    id uuid primary key default gen_random_uuid(),
    seller_id uuid not null references auth.users (id) on delete cascade,
    title text not null check (char_length(trim(title)) between 1 and 100),
    description text not null default '' check (char_length(description) <= 2000),
    category text not null check (category in ('Produce', 'Food', 'Home', 'Clothing', 'Other')),
    price numeric(10, 2) not null check (price >= 0),
    photo_paths text[] not null default '{}',
    pickup_area text not null default '' check (char_length(pickup_area) <= 120),
    -- This is the pickup point selected for this listing, not the seller's live location.
    pickup_location extensions.geography (Point, 4326) not null,
    status text not null default 'available'
        check (status in ('available', 'reserved', 'sold')),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

create index if not exists listings_pickup_location_gix
    on public.listings using gist (pickup_location);
create index if not exists listings_seller_id_idx
    on public.listings (seller_id);
create index if not exists listings_status_idx
    on public.listings (status);

alter table public.listings enable row level security;
revoke all on table public.listings from anon, authenticated;
grant select, insert, update, delete on table public.listings to authenticated;

drop policy if exists "Signed-in users can view available listings and their own" on public.listings;
create policy "Signed-in users can view available listings and their own"
    on public.listings for select to authenticated
    using (status = 'available' or seller_id = (select auth.uid()));

drop policy if exists "Sellers can create their own listings" on public.listings;
create policy "Sellers can create their own listings"
    on public.listings for insert to authenticated
    with check (seller_id = (select auth.uid()));

drop policy if exists "Sellers can update their own listings" on public.listings;
create policy "Sellers can update their own listings"
    on public.listings for update to authenticated
    using (seller_id = (select auth.uid()))
    with check (seller_id = (select auth.uid()));

drop policy if exists "Sellers can delete their own listings" on public.listings;
create policy "Sellers can delete their own listings"
    on public.listings for delete to authenticated
    using (seller_id = (select auth.uid()));

create or replace function public.get_nearby_listings(
    center_latitude double precision,
    center_longitude double precision,
    radius_meters integer default 5000
)
returns table (
    id uuid,
    seller_id uuid,
    title text,
    description text,
    category text,
    price numeric,
    photo_paths text[],
    pickup_area text,
    status text,
    created_at timestamptz,
    latitude double precision,
    longitude double precision,
    distance_meters double precision
)
language sql
stable
security invoker
set search_path = ''
as $$
    select
        l.id,
        l.seller_id,
        l.title,
        l.description,
        l.category,
        l.price,
        l.photo_paths,
        l.pickup_area,
        l.status,
        l.created_at,
        extensions.st_y(l.pickup_location::extensions.geometry) as latitude,
        extensions.st_x(l.pickup_location::extensions.geometry) as longitude,
        extensions.st_distance(
            l.pickup_location,
            extensions.st_setsrid(
                extensions.st_makepoint(center_longitude, center_latitude),
                4326
            )::extensions.geography
        ) as distance_meters
    from public.listings as l
    where l.status = 'available'
      and center_latitude between -90 and 90
      and center_longitude between -180 and 180
      and extensions.st_dwithin(
          l.pickup_location,
          extensions.st_setsrid(
              extensions.st_makepoint(center_longitude, center_latitude),
              4326
          )::extensions.geography,
          greatest(1000, least(coalesce(radius_meters, 5000), 20000))
      )
    order by distance_meters asc;
$$;

revoke all on function public.get_nearby_listings(double precision, double precision, integer) from public;
grant execute on function public.get_nearby_listings(double precision, double precision, integer) to authenticated;
