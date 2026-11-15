-- Migration: 20261115000000_customer_driver_access_self_select.sql
-- Project: xrrhbnnzjqkqnqdvayjw (JTBazar)
--
-- Root cause:
--   `storage.objects` INSERT policy `driver_documents_owner_insert` checks
--   `EXISTS (SELECT 1 FROM public.customer_driver_access ...)` while the
--   `public.customer_driver_access` table only had `customer_driver_access_admin_read`
--   (`private.has_role('admin'::app_role)`). Authenticated drivers could not read
--   their own `customer_driver_access` row, causing driver document uploads to fail RLS.
--
-- Security guarantees preserved:
--   1. `driver-documents` remains private (`public = false`).
--   2. `customer_driver_access_admin_read` remains unchanged.
--   3. `driver_documents_owner_insert` and `storage.foldername(name)[1] = auth.uid()` remain unchanged.
--   4. Authenticated drivers can SELECT ONLY their own `customer_driver_access` row (`user_id = auth.uid()`).
--   5. No INSERT/UPDATE/DELETE privileges or policies are granted to drivers on `customer_driver_access`.

BEGIN;

ALTER TABLE public.customer_driver_access ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS customer_driver_access_self_read
  ON public.customer_driver_access;

CREATE POLICY customer_driver_access_self_read
  ON public.customer_driver_access
  FOR SELECT
  TO authenticated
  USING (user_id = auth.uid());

-- Defensive invariant assertions: abort transaction if any security prerequisite is violated.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM storage.buckets
    WHERE id = 'driver-documents'
      AND public IS TRUE
  ) THEN
    RAISE EXCEPTION 'Security invariant failed: driver-documents bucket must remain private (public = false)';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_policies
    WHERE schemaname = 'public'
      AND tablename = 'customer_driver_access'
      AND policyname = 'customer_driver_access_admin_read'
  ) THEN
    RAISE EXCEPTION 'Security invariant failed: customer_driver_access_admin_read policy is missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1
    FROM pg_policies
    WHERE schemaname = 'storage'
      AND tablename = 'objects'
      AND policyname = 'driver_documents_owner_insert'
  ) THEN
    RAISE EXCEPTION 'Security invariant failed: driver_documents_owner_insert policy is missing';
  END IF;
END
$$;

COMMIT;
