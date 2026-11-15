-- Performance indexes for Alibaba-scale paginated product queries (100 - 10,000+ listings)
-- Covers published + approved + visible storefront filters and category/price/text lookups.
-- Note: Create and verify in staging before production deployment.

create extension if not exists pg_trgm;

-- Fast default feed & newest pagination
create index if not exists idx_products_published_created_at
  on public.products (created_at desc, id desc)
  where publication_status = 'published'
    and moderation_status = 'approved'
    and vendor_hidden = false;

-- Fast category & subcategory paginated filtering
create index if not exists idx_products_published_category_created
  on public.products (category_id, created_at desc, id desc)
  where publication_status = 'published'
    and moderation_status = 'approved'
    and vendor_hidden = false;

-- Fast price sorting and budget shopping filtering
create index if not exists idx_products_published_price
  on public.products (price asc, id desc)
  where publication_status = 'published'
    and moderation_status = 'approved'
    and vendor_hidden = false;

create index if not exists idx_products_published_discount
  on public.products (discount_percent desc, created_at desc, id desc)
  where publication_status = 'published'
    and moderation_status = 'approved'
    and vendor_hidden = false;

-- Fast trigram search for ilike queries across large product catalogs
create index if not exists idx_products_name_trgm
  on public.products using gin (name gin_trgm_ops);

-- Fast primary image and variant batch enrichment per page
create index if not exists idx_product_images_product_primary_sort
  on public.product_images (product_id, is_primary desc, sort_order asc);

create index if not exists idx_product_variants_product_active_sort
  on public.product_variants (product_id, sort_order asc)
  where is_active = true;

-- Fast hierarchical category tree resolution
create index if not exists idx_categories_active_parent_sort
  on public.categories (is_active, parent_id, sort_order asc);
