-- Reference migration: 20261117220000_customer_generate_delivery_otp_email_only.sql
-- Purpose:
--   Align `public.customer_generate_delivery_otp(p_order_id uuid)` with zero-plaintext-OTP security:
--     1. Verify authenticated session (`auth.uid() IS NOT NULL`).
--     2. Verify order ownership (`orders.customer_id = auth.uid()`).
--     3. Verify a valid `driver_order_assignments` row exists AND `status = 'picked_up'`.
--     4. Raise distinct descriptive error messages for wrong customer, missing driver assignment,
--        and parcel not yet picked up (instead of a single opaque 42501 error).
--     5. Store ONLY a cryptographic hash of the 6-digit OTP with a 30-minute expiry (`expires_at`)
--        and reset verification attempts (`otp_attempts = 0`).
--     6. Never return the plaintext OTP in the RPC response; send it only to the customer's
--        registered email via backend mailer / notification pipeline.

BEGIN;

CREATE OR REPLACE FUNCTION public.customer_generate_delivery_otp(p_order_id uuid)
RETURNS jsonb
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = public, extensions
AS $$
DECLARE
  v_uid uuid := auth.uid();
  v_order record;
  v_assignment record;
  v_customer_email text;
  v_otp_plain text;
  v_otp_hash text;
  v_expires_at timestamptz := now() + interval '30 minutes';
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'Authentication required: please sign in to request a delivery OTP.'
      USING ERRCODE = '42501';
  END IF;

  SELECT id, customer_id, customer_email, status, order_number
    INTO v_order
  FROM public.orders
  WHERE id = p_order_id
  LIMIT 1;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'Order not found.'
      USING ERRCODE = 'P0002';
  END IF;

  IF v_order.customer_id IS DISTINCT FROM v_uid THEN
    RAISE EXCEPTION 'Wrong customer: this order belongs to a different account.'
      USING ERRCODE = '42501';
  END IF;

  SELECT id, driver_id, status
    INTO v_assignment
  FROM public.driver_order_assignments
  WHERE order_id = p_order_id
    AND status NOT IN ('cancelled', 'failed')
  ORDER BY updated_at DESC NULLS LAST, created_at DESC
  LIMIT 1;

  IF NOT FOUND THEN
    RAISE EXCEPTION 'No driver assignment found for this order yet.'
      USING ERRCODE = 'P0002';
  END IF;

  IF lower(coalesce(v_assignment.status, '')) NOT IN ('picked_up', 'in_transit') THEN
    RAISE EXCEPTION 'Parcel has not been picked up by the driver yet.'
      USING ERRCODE = 'P0001';
  END IF;

  SELECT nullif(trim(coalesce(u.email, v_order.customer_email, '')), '')
    INTO v_customer_email
  FROM auth.users u
  WHERE u.id = v_uid;

  IF v_customer_email IS NULL THEN
    RAISE EXCEPTION 'Registered customer email address is required to send the delivery OTP.'
      USING ERRCODE = 'P0001';
  END IF;

  -- Generate 6-digit OTP using CSPRNG and store ONLY its bcrypt/pgcrypto hash.
  v_otp_plain := lpad(((('x' || substr(encode(extensions.gen_random_bytes(4), 'hex'), 1, 8))::bit(32)::int & 2147483647) % 1000000)::text, 6, '0');
  v_otp_hash := extensions.crypt(v_otp_plain, extensions.gen_salt('bf'));

  UPDATE public.driver_order_assignments
  SET
    delivery_otp_hash = v_otp_hash,
    delivery_otp_expires_at = v_expires_at,
    delivery_otp_attempts = 0,
    delivery_otp_verified_at = NULL,
    updated_at = now()
  WHERE id = v_assignment.id;

  -- Dispatch email via backend email queue/function (never return v_otp_plain in RPC output).
  PERFORM pg_notify(
    'jtbazar_delivery_otp_email',
    jsonb_build_object(
      'order_id', p_order_id,
      'order_number', v_order.order_number,
      'assignment_id', v_assignment.id,
      'recipient_email', v_customer_email,
      'otp_code', v_otp_plain,
      'expires_at', v_expires_at
    )::text
  );

  RETURN jsonb_build_object(
    'sent', true,
    'expires_in_minutes', 30
  );
END;
$$;

REVOKE ALL ON FUNCTION public.customer_generate_delivery_otp(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.customer_generate_delivery_otp(uuid) TO authenticated;

COMMIT;
