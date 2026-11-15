-- Transactional RLS verification suite for driver document uploads (Project: xrrhbnnzjqkqnqdvayjw)
-- Run this against a Supabase development branch after applying
-- `20261115000000_customer_driver_access_self_select.sql`.
-- All test data is rolled back at the end.

BEGIN;

DO $$
DECLARE
  v_enabled_driver_id uuid := '11111111-1111-4111-8111-111111111111';
  v_disabled_driver_id uuid := '22222222-2222-4222-8222-222222222222';
  v_admin_user_id uuid := '33333333-3333-4333-8333-333333333333';
  v_count integer;
  v_bucket_public boolean;
BEGIN
  -- 0. Ensure bucket exists and stays private (public = false)
  SELECT public INTO v_bucket_public
  FROM storage.buckets
  WHERE id = 'driver-documents';

  IF v_bucket_public IS DISTINCT FROM FALSE THEN
    RAISE EXCEPTION 'FAIL [8]: driver-documents bucket must exist and have public = false (actual: %)', v_bucket_public;
  END IF;

  -- Seed ephemeral test users & customer_driver_access rows (if test users don't exist yet)
  INSERT INTO auth.users (id, email, aud, role)
  VALUES
    (v_enabled_driver_id, 'enabled-driver-rls-test@jtbazar.invalid', 'authenticated', 'authenticated'),
    (v_disabled_driver_id, 'disabled-driver-rls-test@jtbazar.invalid', 'authenticated', 'authenticated'),
    (v_admin_user_id, 'admin-rls-test@jtbazar.invalid', 'authenticated', 'authenticated')
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO public.customer_driver_access (user_id, enabled)
  VALUES
    (v_enabled_driver_id, true),
    (v_disabled_driver_id, false)
  ON CONFLICT (user_id) DO UPDATE SET enabled = EXCLUDED.enabled;

  -- 1. Enabled driver can SELECT their own customer_driver_access row
  PERFORM set_config('role', 'authenticated', true);
  PERFORM set_config('request.jwt.claim.sub', v_enabled_driver_id::text, true);
  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_enabled_driver_id::text, 'role', 'authenticated')::text, true);

  SELECT count(*) INTO v_count
  FROM public.customer_driver_access
  WHERE user_id = v_enabled_driver_id;

  IF v_count <> 1 THEN
    RAISE EXCEPTION 'FAIL [1]: Enabled driver expected 1 self row, got %', v_count;
  END IF;

  -- 2. Enabled driver cannot SELECT another user's customer_driver_access row
  SELECT count(*) INTO v_count
  FROM public.customer_driver_access
  WHERE user_id = v_disabled_driver_id;

  IF v_count <> 0 THEN
    RAISE EXCEPTION 'FAIL [2]: Enabled driver read another user row (count=%)', v_count;
  END IF;

  -- Verify driver cannot self-enable or insert/update customer_driver_access
  BEGIN
    UPDATE public.customer_driver_access SET enabled = false WHERE user_id = v_enabled_driver_id;
    -- Zero rows updated under RLS or 42501 error is expected; verify enabled is unchanged
    PERFORM set_config('role', 'postgres', true);
    IF NOT (SELECT enabled FROM public.customer_driver_access WHERE user_id = v_enabled_driver_id) THEN
      RAISE EXCEPTION 'FAIL [2b]: Driver was able to modify customer_driver_access';
    END IF;
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  -- 3. Enabled driver can upload cnic_front, cnic_back, vehicle_plate to own UID folder
  PERFORM set_config('role', 'authenticated', true);
  PERFORM set_config('request.jwt.claim.sub', v_enabled_driver_id::text, true);
  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_enabled_driver_id::text, 'role', 'authenticated')::text, true);

  INSERT INTO storage.objects (bucket_id, name)
  VALUES
    ('driver-documents', v_enabled_driver_id::text || '/cnic_front_1700000000001.jpg'),
    ('driver-documents', v_enabled_driver_id::text || '/cnic_back_1700000000002.jpg'),
    ('driver-documents', v_enabled_driver_id::text || '/vehicle_plate_1700000000003.jpg');

  -- 4. Disabled driver is blocked from uploading to driver-documents
  PERFORM set_config('request.jwt.claim.sub', v_disabled_driver_id::text, true);
  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_disabled_driver_id::text, 'role', 'authenticated')::text, true);

  BEGIN
    INSERT INTO storage.objects (bucket_id, name)
    VALUES ('driver-documents', v_disabled_driver_id::text || '/cnic_front_1700000000004.jpg');
    RAISE EXCEPTION 'FAIL [4]: Disabled driver unexpectedly succeeded uploading to driver-documents';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  -- 5. Enabled driver uploading into another user's UID folder is blocked
  PERFORM set_config('request.jwt.claim.sub', v_enabled_driver_id::text, true);
  PERFORM set_config('request.jwt.claims', json_build_object('sub', v_enabled_driver_id::text, 'role', 'authenticated')::text, true);

  BEGIN
    INSERT INTO storage.objects (bucket_id, name)
    VALUES ('driver-documents', v_disabled_driver_id::text || '/cnic_front_1700000000005.jpg');
    RAISE EXCEPTION 'FAIL [5]: Enabled driver unexpectedly uploaded into another user UID folder';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  -- 6. Unauthenticated (anon) user cannot read customer_driver_access or upload to driver-documents
  PERFORM set_config('role', 'anon', true);
  PERFORM set_config('request.jwt.claim.sub', '', true);
  PERFORM set_config('request.jwt.claims', '{"role":"anon"}', true);

  BEGIN
    SELECT count(*) INTO v_count FROM public.customer_driver_access;
    IF v_count <> 0 THEN
      RAISE EXCEPTION 'FAIL [6a]: Anon user read customer_driver_access rows (count=%)', v_count;
    END IF;
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  BEGIN
    INSERT INTO storage.objects (bucket_id, name)
    VALUES ('driver-documents', v_enabled_driver_id::text || '/cnic_front_1700000000006.jpg');
    RAISE EXCEPTION 'FAIL [6b]: Anon user unexpectedly uploaded to driver-documents';
  EXCEPTION WHEN insufficient_privilege THEN
    NULL;
  END;

  -- 7 & 8. Reset role and verify admin policy + self policy + storage insert policy remain intact
  PERFORM set_config('role', 'postgres', true);

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'customer_driver_access'
      AND policyname = 'customer_driver_access_admin_read'
  ) THEN
    RAISE EXCEPTION 'FAIL [7]: customer_driver_access_admin_read missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'public' AND tablename = 'customer_driver_access'
      AND policyname = 'customer_driver_access_self_read'
  ) THEN
    RAISE EXCEPTION 'FAIL [8a]: customer_driver_access_self_read missing';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'storage' AND tablename = 'objects'
      AND policyname = 'driver_documents_owner_insert'
  ) THEN
    RAISE EXCEPTION 'FAIL [8b]: driver_documents_owner_insert missing';
  END IF;

  RAISE NOTICE 'ALL 8 DRIVER DOCUMENTS RLS VERIFICATION CHECKS PASSED';
END
$$;

ROLLBACK;
