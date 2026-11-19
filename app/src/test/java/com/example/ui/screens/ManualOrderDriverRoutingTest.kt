package com.example.ui.screens

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualOrderDriverRoutingTest {
  @Test
  fun manualAssignmentsUseManualLifecycleRpcNames() {
    val online = JSONObject().put("order_type", "online")
    val manual = JSONObject().put("order_type", "manual")

    assertFalse(isManualOrderDelivery(online))
    assertTrue(isManualOrderDelivery(manual))
    assertEquals(
      "driver_verify_delivery_otp",
      driverDeliveryRpcName(online, "driver_verify_delivery_otp", "driver_verify_manual_order_delivery_otp")
    )
    assertEquals(
      "driver_verify_manual_order_delivery_otp",
      driverDeliveryRpcName(manual, "driver_verify_delivery_otp", "driver_verify_manual_order_delivery_otp")
    )
  }
}
