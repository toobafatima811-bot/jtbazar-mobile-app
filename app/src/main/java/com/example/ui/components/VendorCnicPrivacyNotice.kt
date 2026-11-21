package com.example.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.example.localization.appText
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorSpacing

/**
 * Explains JTBazar's CNIC use and retention rules before a vendor submits or changes
 * identity details. Acknowledgement is deliberately held only in Compose state.
 */
@Composable
fun VendorCnicPrivacyNotice(
  acknowledged: Boolean,
  onAcknowledgedChange: (Boolean) -> Unit,
  modifier: Modifier = Modifier
) {
  AppCard(
    modifier = modifier,
    borderColor = VendorColors.NavyHeader.copy(alpha = 0.2f)
  ) {
    Column(verticalArrangement = Arrangement.spacedBy(VendorSpacing.sm)) {
      Text(
        text = appText("CNIC privacy & document protection", "شناختی کارڈ کی رازداری اور دستاویزات کا تحفظ"),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = VendorColors.TextPrimary
      )
      Text(
        text = appText(
          "Why we collect it: Your CNIC number and front/back images help verify the person responsible for this seller account, prevent impersonation and fraud, protect customers, and investigate genuine disputes or lawful requests.",
          "ہم کیوں لیتے ہیں: آپ کا شناختی کارڈ نمبر اور سامنے/پیچھے کی تصاویر اس سیلر اکاؤنٹ کے ذمہ دار شخص کی تصدیق، جعل سازی اور دھوکہ دہی کی روک تھام، صارفین کے تحفظ، اور حقیقی تنازعات یا قانونی درخواستوں کی جانچ میں مدد دیتی ہیں۔"
        ),
        style = MaterialTheme.typography.bodySmall,
        color = VendorColors.TextSecondary
      )
      Text(
        text = appText(
          "Who can access it: Only authorised JTBazar administrators may review these documents through the secured admin dashboard. Vendors may access only their own files where existing permissions allow. Admin document views are audited. Files stay in private storage; they are not public, used for advertising, sold, or shared with other vendors.",
          "رسائی کس کو ہے: صرف جے ٹی بازار کے مجاز منتظمین محفوظ ایڈمن ڈیش بورڈ کے ذریعے ان دستاویزات کا جائزہ لے سکتے ہیں۔ موجودہ اجازتوں کے مطابق فروخت کنندہ صرف اپنی فائلیں دیکھ سکتا ہے۔ ایڈمن کی جانب سے دستاویز دیکھنے کا آڈٹ ریکارڈ رکھا جاتا ہے۔ فائلیں نجی اسٹوریج میں رہتی ہیں؛ انہیں عوامی نہیں کیا جاتا، اشتہارات کے لیے استعمال یا فروخت نہیں کیا جاتا، اور دوسرے فروخت کنندگان سے شیئر نہیں کیا جاتا۔"
        ),
        style = MaterialTheme.typography.bodySmall,
        color = VendorColors.TextSecondary
      )
      Text(
        text = appText(
          "Retention: Rejected applications — CNIC number and images are kept for up to 90 days after the final rejection, then securely deleted. Approved vendors — CNIC data is kept while the vendor relationship is active; after the account is formally closed, it is deleted within 90 days. If a legal requirement, active dispute, or fraud investigation may affect deletion, contact JTBazar support promptly so the case can be reviewed.",
          "محفوظ رکھنے کی مدت: مسترد درخواستیں — آخری فیصلے کے بعد شناختی کارڈ نمبر اور تصاویر زیادہ سے زیادہ 90 دن رکھی جائیں گی، پھر محفوظ طریقے سے حذف ہوں گی۔ منظور شدہ فروخت کنندگان — شناختی معلومات فعال کاروباری تعلق کے دوران رکھی جائیں گی؛ اکاؤنٹ باضابطہ بند ہونے کے بعد 90 دن کے اندر حذف ہوں گی۔ اگر قانونی تقاضا، جاری تنازع یا دھوکہ دہی کی تفتیش حذف کرنے کے عمل پر اثر انداز ہو سکتی ہو تو فوری طور پر جے ٹی بازار سپورٹ سے رابطہ کریں تاکہ معاملے کا جائزہ لیا جا سکے۔"
        ),
        style = MaterialTheme.typography.bodySmall,
        color = VendorColors.TextSecondary
      )
      Text(
        text = appText(
          "Business verification PDFs are separate from CNIC records and follow their own retention requirements. For help, open Vendor Center → More → Cases & Support.",
          "کاروباری تصدیقی PDF شناختی کارڈ کے ریکارڈ سے الگ ہیں اور ان کی اپنی محفوظ رکھنے کی مدت ہے۔ مدد کے لیے Vendor Center → More → Cases & Support کھولیں۔"
        ),
        style = MaterialTheme.typography.bodySmall,
        color = VendorColors.TextSecondary
      )
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .clickable { onAcknowledgedChange(!acknowledged) },
        verticalAlignment = Alignment.CenterVertically
      ) {
        Checkbox(
          checked = acknowledged,
          onCheckedChange = onAcknowledgedChange
        )
        Text(
          text = appText(
            "I have read and understood this CNIC privacy notice.",
            "میں نے شناختی کارڈ کی رازداری کا یہ نوٹس پڑھ اور سمجھ لیا ہے۔"
          ),
          style = MaterialTheme.typography.bodySmall,
          fontWeight = FontWeight.SemiBold,
          color = VendorColors.TextPrimary
        )
      }
    }
  }
}
