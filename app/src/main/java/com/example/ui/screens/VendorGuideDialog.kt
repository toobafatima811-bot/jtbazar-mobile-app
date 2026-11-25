package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.localization.AppLanguageManager
import com.example.ui.theme.VendorColors
import com.example.ui.theme.VendorShapes

private data class VendorGuideSection(val title: String, val body: String)

@Composable
fun VendorGuideDialog(onDismiss: () -> Unit) {
  var language by remember { mutableStateOf(if (AppLanguageManager.isUrdu) "ur" else "en") }

  val english = listOf(
    VendorGuideSection("1. Dashboard overview", "Overview shows listing counts, recent products, latest orders and action items. Use Add Product to create a listing, Update Stock to correct quantities and View Orders to process customer purchases."),
    VendorGuideSection("2. Listings and product creation", "Open Listings and choose Add Product. Complete product details, pricing, stock, images and description. New listings are sent for JTBazar review and may not appear publicly until approved."),
    VendorGuideSection("3. Product details — every field", "Product name*: Exact customer-facing product name. Product URL*: Page slug generated from the name; use lowercase English letters, numbers and hyphens. Product code: Automatic SKU; normally leave unchanged. Category*: Choose the most accurate category because commission and special fields depend on it."),
    VendorGuideSection("4. Price, commission and discount", "Your base price*: Amount you want to receive for one product (for food, per selected selling unit). JTBazar adds its configured commission automatically to calculate customer price. Check Price breakdown. Discount (%): Optional discount applied to customer price after commission; enter 0 if none."),
    VendorGuideSection("5. Food-only fields", "Selling unit: kg, g, litre, ml, piece, dozen or pack. Customer quantity step: Amount customers can add each time; for kg/litre enter grams/ml, e.g. 250. Minimum purchase steps: Minimum number of steps to buy. Daily item: Enable only for items whose availability changes daily. Daily availability: Available today, Not for today or Coming soon. Today's available quantity: Enter only when available today. Food delivery is limited to 5 km; add shop coordinates in Shop Settings."),
    VendorGuideSection("6. Stock, cost and profit", "Initial stock quantity: Starting quantity for a new listing. Track inventory: Keep enabled when stock should be tracked. Shipping weight (grams): Packed product weight. Supplier cost: Private purchase cost, not shown to customers. Fulfilment expense: Per-item packing/handling cost. Minimum profit: Your target minimum profit. Enter real costs and review the profit preview."),
    VendorGuideSection("7. Product variants", "Use variants for size, colour or material. Option name: e.g. Size. Option value: e.g. Large or Black. Variant SKU: Optional unique code. Price override: Leave blank to use base price or enter a different price. Stock quantity: Stock for this option. Variant image: Clear image for that option. Variants are reviewed before publication."),
    VendorGuideSection("8. Photos, description and search", "Product images*: At least one clear image is required; maximum 8 images, JPG/PNG/WebP/AVIF, up to 10 MB each. Short description: Customer-friendly summary, up to 300 characters. Full description: Features, material, size, use and package contents. Specifications (JSON array) and Dimensions (JSON object): Valid JSON only; leave defaults if unsure. SEO title and SEO description: Optional search-result title and summary."),
    VendorGuideSection("9. Inventory, orders and fulfilment", "Keep available quantities accurate in Inventory and record stock changes. In Orders/Fulfilment, review new orders, prepare the correct items and update statuses on time. Never mark an order dispatched or completed before it actually happens."),
    VendorGuideSection("10. Cases, support and shop settings", "Cases & Support: Read the customer issue, reply politely and attach relevant evidence when requested. Shop Settings: Keep shop name, contact, location and opening hours accurate. Shop enabled can pause the shop in an emergency. Weekly hours determine public Open/Closed status using Pakistan time."),
    VendorGuideSection("11. Before submitting", "Use genuine product photos and accurate descriptions. Select the correct category and variant stock. Never put supplier cost or private business information in the public description. Check price, discount, weight and stock. Submit once and wait for review; do not create duplicate listings.")
  )

  val urdu = listOf(
    VendorGuideSection("۱۔ ڈیش بورڈ کا جائزہ", "Overview میں پروڈکٹس کی تعداد، حالیہ لسٹنگز، تازہ آرڈرز اور ضروری کام نظر آتے ہیں۔ Add Product سے پروڈکٹ شامل کریں، Update Stock سے مقدار درست کریں اور View Orders سے کسٹمر آرڈرز دیکھیں۔"),
    VendorGuideSection("۲۔ لسٹنگز اور نئی پروڈکٹ", "Listings کھولیں اور Add Product منتخب کریں۔ پروڈکٹ کی تفصیلات، قیمت، اسٹاک، تصاویر اور وضاحت مکمل کریں۔ نئی لسٹنگ JTBazar کی جانچ کے لیے جاتی ہے اور منظوری سے پہلے عوامی طور پر نظر نہیں آ سکتی۔"),
    VendorGuideSection("۳۔ پروڈکٹ کی تفصیلات — ہر فیلڈ", "Product name*: کسٹمر کو دکھایا جانے والا درست نام۔ Product URL*: پروڈکٹ صفحے کا لنک/slug، جو نام سے خود بنتا ہے؛ اس میں چھوٹے انگریزی حروف، نمبر اور ہائفن استعمال ہوتے ہیں۔ Product code: خودکار SKU ہے، عام طور پر تبدیل نہ کریں۔ Category*: درست کیٹیگری منتخب کریں کیونکہ کمیشن اور اضافی فیلڈز اسی پر منحصر ہیں۔"),
    VendorGuideSection("۴۔ قیمت، کمیشن اور ڈسکاؤنٹ", "Your base price*: وہ رقم جو آپ ایک پروڈکٹ کے بدلے وصول کرنا چاہتے ہیں؛ فوڈ میں منتخب یونٹ کے حساب سے۔ JTBazar کمیشن خود شامل کرکے کسٹمر کی قیمت بناتا ہے۔ Price breakdown ضرور دیکھیں۔ Discount (%): کمیشن کے بعد کسٹمر کی قیمت پر اختیاری رعایت؛ رعایت نہ ہو تو 0 رکھیں۔"),
    VendorGuideSection("۵۔ صرف فوڈ پروڈکٹس کے فیلڈز", "Selling unit: کلو، گرام، لیٹر، ملی لیٹر، پیس، درجن یا پیک منتخب کریں۔ Customer quantity step: کسٹمر ایک بار میں کتنی مقدار خرید سکتا ہے؛ کلو/لیٹر کے لیے گرام یا ملی لیٹر لکھیں، مثلاً 250۔ Minimum purchase steps: کم از کم خریداری کے مراحل۔ Daily item: صرف روزانہ دستیابی بدلنے والی ڈش کے لیے آن کریں۔ Daily availability: آج دستیاب، آج دستیاب نہیں یا جلد دستیاب۔ Today's available quantity: صرف آج دستیاب ہونے پر مقدار لکھیں۔ فوڈ ڈیلیوری 5 کلومیٹر تک ہے؛ Shop Settings میں دکان کے کوآرڈینیٹس درج کریں۔"),
    VendorGuideSection("۶۔ اسٹاک، لاگت اور منافع", "Initial stock quantity: نئی لسٹنگ کا ابتدائی اسٹاک۔ Track inventory: اسٹاک کی گنتی رکھنی ہو تو آن رکھیں۔ Shipping weight (grams): پیک شدہ پروڈکٹ کا وزن۔ Supplier cost: آپ کی نجی خریداری لاگت، کسٹمر کو نظر نہیں آتی۔ Fulfilment expense: فی آئٹم پیکنگ/ہینڈلنگ خرچ۔ Minimum profit: کم از کم مطلوبہ منافع۔ اصل اخراجات درج کرکے منافع کا پری ویو دیکھیں۔"),
    VendorGuideSection("۷۔ پروڈکٹ ویریئنٹس", "سائز، رنگ یا میٹریل کے لیے variants استعمال کریں۔ Option name: مثلاً Size۔ Option value: مثلاً Large یا Black۔ Variant SKU: اختیاری منفرد کوڈ۔ Price override: خالی چھوڑیں تو بنیادی قیمت لگے گی، ورنہ الگ قیمت لکھیں۔ Stock quantity: اس آپشن کا اسٹاک۔ Variant image: اسی آپشن کی واضح تصویر۔ ویریئنٹس بھی اشاعت سے پہلے جانچ کے لیے جاتے ہیں۔"),
    VendorGuideSection("۸۔ تصاویر، تفصیل اور سرچ", "Product images*: کم از کم ایک واضح تصویر لازمی؛ زیادہ سے زیادہ 8 تصاویر، JPG/PNG/WebP/AVIF، ہر تصویر 10 MB تک۔ Short description: 300 حروف تک مختصر تعارف۔ Full description: خصوصیات، میٹریل، سائز، استعمال اور پیکج میں شامل چیزیں لکھیں۔ Specifications (JSON array) اور Dimensions (JSON object): صرف درست JSON؛ سمجھ نہ آئے تو ڈیفالٹ رہنے دیں۔ SEO title اور SEO description: سرچ رزلٹس کے لیے اختیاری عنوان اور خلاصہ۔"),
    VendorGuideSection("۹۔ اسٹاک، آرڈرز اور ترسیل", "Inventory میں دستیاب مقدار درست رکھیں اور تبدیلی ریکارڈ کریں۔ Orders/Fulfilment میں نیا آرڈر دیکھیں، درست اشیا تیار کریں اور وقت پر اسٹیٹس اپ ڈیٹ کریں۔ حقیقت میں بھیجنے یا مکمل کرنے سے پہلے آرڈر کو dispatched یا completed نہ کریں۔"),
    VendorGuideSection("۱۰۔ کیسز، سپورٹ اور دکان کی ترتیبات", "Cases & Support میں کسٹمر کا مسئلہ پڑھیں، شائستگی سے جواب دیں اور ضرورت پر ثبوت لگائیں۔ Shop Settings میں دکان کا نام، رابطہ، مقام اور اوقات درست رکھیں۔ ہنگامی صورت میں Shop enabled بند کرکے دکان عارضی طور پر روک سکتے ہیں۔ ہفتہ وار اوقات پاکستان کے وقت کے مطابق Open/Closed اسٹیٹس طے کرتے ہیں۔"),
    VendorGuideSection("۱۱۔ جمع کرانے سے پہلے", "اصل تصاویر اور درست تفصیل استعمال کریں۔ درست کیٹیگری اور ویریئنٹ اسٹاک منتخب کریں۔ عوامی تفصیل میں سپلائر کی لاگت یا نجی معلومات نہ لکھیں۔ قیمت، رعایت، وزن اور اسٹاک چیک کریں۔ ایک بار جمع کرا کے جانچ کا انتظار کریں؛ ڈپلیکیٹ لسٹنگ نہ بنائیں۔")
  )

  val roman = listOf(
    VendorGuideSection("1. Dashboard ka overview", "Overview mein products ki tadaad, recent listings, latest orders aur zaroori actions nazar aate hain. Add Product se product add karein, Update Stock se quantity durust karein aur View Orders se customer orders dekhein."),
    VendorGuideSection("2. Listings aur naya product", "Listings kholein aur Add Product select karein. Product details, price, stock, images aur description mukammal karein. Nayi listing JTBazar review ke liye jati hai; approval se pehle public nahi hoti."),
    VendorGuideSection("3. Product details — har field", "Product name*: Customer ko nazar anay wala sahi naam. Product URL*: Product page ka slug jo naam se khud banta hai; lowercase English letters, numbers aur hyphens use karta hai. Product code: Automatic SKU, aam tor par change na karein. Category*: Sab se relevant category select karein; commission aur extra fields isi par depend karti hain."),
    VendorGuideSection("4. Price, commission aur discount", "Your base price*: Woh amount jo aap ek product ke badlay receive karna chahte hain; food mein selected unit ke hisaab se. JTBazar commission khud add karke customer price calculate karta hai. Price breakdown check karein. Discount (%): Commission ke baad customer price par optional discount; na ho to 0 rakhein."),
    VendorGuideSection("5. Sirf food products ke fields", "Selling unit: kg, g, litre, ml, piece, dozen ya pack. Customer quantity step: Customer ek dafa mein kitni quantity le sakta hai; kg/litre ke liye grams ya ml likhein, misal 250. Minimum purchase steps: Minimum purchase steps. Daily item: Sirf roz availability badalne wali dish ke liye on karein. Daily availability: Available today, Not for today ya Coming soon. Today's available quantity: Sirf aaj available honay par quantity likhein. Food delivery 5 km tak hai; Shop Settings mein shop coordinates add karein."),
    VendorGuideSection("6. Stock, cost aur profit", "Initial stock quantity: Naye product ka starting stock. Track inventory: Stock track karna ho to on rakhein. Shipping weight (grams): Packed product ka weight. Supplier cost: Aapki private purchase cost, customer ko nazar nahi aati. Fulfilment expense: Har item ki packing/handling cost. Minimum profit: Aapka target minimum profit. Asal costs likh kar profit preview check karein."),
    VendorGuideSection("7. Product variants", "Size, color ya material ke liye variants use karein. Option name: misal Size. Option value: misal Large ya Black. Variant SKU: Optional unique code. Price override: Khali chhorein to base price, warna alag price. Stock quantity: Is option ka stock. Variant image: Is option ki clear image. Variants publish honay se pehle review hotay hain."),
    VendorGuideSection("8. Photos, description aur search", "Product images*: Kam az kam ek clear image lazmi; maximum 8 images, JPG/PNG/WebP/AVIF, har image 10 MB tak. Short description: 300 characters tak mukhtasar intro. Full description: Features, material, size, use aur package contents batayein. Specifications (JSON array) aur Dimensions (JSON object): Sirf valid JSON; samajh na aaye to default rehne dein. SEO title aur SEO description: Search results ke liye optional title aur summary."),
    VendorGuideSection("9. Inventory, orders aur fulfilment", "Inventory mein available quantity durust rakhein aur changes record karein. Orders/Fulfilment mein naya order dekhein, sahi items prepare karein aur time par status update karein. Parcel waqai dispatch ya complete honay se pehle order ko dispatched/completed mark na karein."),
    VendorGuideSection("10. Cases, support aur shop settings", "Cases & Support mein customer ka issue parhein, tameez se jawab dein aur zaroorat par evidence attach karein. Shop Settings mein shop name, contact, location aur opening hours sahi rakhein. Emergency mein Shop enabled off karke shop pause kar sakte hain. Weekly hours Pakistan time ke mutabiq Open/Closed status set karte hain."),
    VendorGuideSection("11. Submit se pehle", "Original photos aur accurate details use karein. Sahi category aur variant stock select karein. Public description mein supplier cost ya private business info na likhein. Price, discount, weight aur stock check karein. Ek dafa submit karke review ka wait karein; duplicate listing na banayein.")
  )

  val sections = when (language) {
    "ur" -> urdu
    "roman" -> roman
    else -> english
  }

  CompositionLocalProvider(LocalLayoutDirection provides if (language == "ur") LayoutDirection.Rtl else LayoutDirection.Ltr) {
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = VendorColors.CardWhite,
    shape = VendorShapes.card,
    title = {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
          text = when (language) { "ur" -> "وینڈر ڈیش بورڈ رہنمائی"; "roman" -> "Vendor Dashboard ki Rahnumai"; else -> "Vendor dashboard guide" },
          fontWeight = FontWeight.Bold
        )
        Text(
          text = when (language) {
            "ur" -> "ہر حصے اور پروڈکٹ فیلڈ کے لیے مرحلہ وار ہدایات۔ * والی فیلڈز لازمی ہیں۔"
            "roman" -> "Har section aur product field ke liye step-by-step hidayat. * wali fields lazmi hain."
            else -> "Step-by-step instructions for every dashboard section and product field. Fields marked * are required."
          },
          style = MaterialTheme.typography.bodySmall,
          color = VendorColors.TextSecondary
        )
      }
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
          OutlinedButton(onClick = { language = "en" }, modifier = Modifier.weight(1f)) { Text("English") }
          OutlinedButton(onClick = { language = "ur" }, modifier = Modifier.weight(1f)) { Text("اردو") }
          OutlinedButton(onClick = { language = "roman" }, modifier = Modifier.weight(1f)) { Text("Roman Urdu") }
        }
        Column(
          modifier = Modifier.heightIn(max = 470.dp).verticalScroll(rememberScrollState()),
          verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
              Text(section.title, fontWeight = FontWeight.Bold, color = VendorColors.TextPrimary)
              Text(section.body, style = MaterialTheme.typography.bodySmall, color = VendorColors.TextSecondary)
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = onDismiss) {
        Text(when (language) { "ur" -> "بند کریں"; "roman" -> "Band karein"; else -> "Close" })
      }
    }
  )
  }
}
