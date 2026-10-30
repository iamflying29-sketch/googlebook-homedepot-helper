package local.hdhelper

/**
 * Where a Home Depot notification should go, worked out from the email's subject and text alone,
 * because that is all a notification carries. Kept deliberately simple and conservative: when in
 * doubt the answer is the Home Depot app itself, never somewhere wrong.
 */
sealed class Target {
    /** A sign-in verification code to copy and type into the app. */
    data class Code(val code: String) : Target()

    /** An order to open by its number (shipped, delivered, rescheduled, cancelled, confirmed). */
    data class Order(val number: String) : Target()

    /** An order email that names no order number in the notification: the purchase history, newest first. */
    object PurchaseHistory : Target()

    /** Everything else: just the Home Depot app. */
    object App : Target()

    companion object {
        const val HD_PACKAGE = "com.thehomedepot"

        /** The order details page the Home Depot app opens natively for its own "Manage Order" email links. */
        const val ORDER_DETAILS = "https://www.homedepot.com/myaccount/order-details?orderNumber="

        /** The purchase history page the Home Depot app opens for its own "Check Order Status" email links. */
        const val PURCHASE_HISTORY = "https://www.homedepot.com/myaccount/purchase-history"

        const val HOME_PAGE = "https://www.homedepot.com/"

        /** "verification code: 714954", "One-Time Code 714954", "your one-time login code is 714954". */
        private val CODE = Regex(
            """(?:verification|one.time(?:\s+login)?|security|sign.in|login)\s*code\s*(?:is|:)?\s*(\d{4,8})\b""",
            RegexOption.IGNORE_CASE)
        private val CODE_MAIL = Regex("""verification code|one.time code|one.time login code""", RegexOption.IGNORE_CASE)
        private val BARE_CODE = Regex("""\b(\d{6})\b""")

        /** Home Depot order numbers: WN74903499, WE15405176, WP60179103, W892859152. */
        private val ORDER = Regex("""\bW[A-Z]{0,2}\d{8,10}\b""")

        private val ORDER_WORDS = Regex(
            """\b(order|deliver|delivery|delivered|shipped|shipping|pickup|pick up|cancel|cancelled|canceled|return|refund|backorder)""",
            RegexOption.IGNORE_CASE)

        fun classify(subject: CharSequence?, text: CharSequence?): Target {
            val all = (subject?.toString() ?: "") + "\n" + (text?.toString() ?: "")
            CODE.find(all)?.let { return Code(it.groupValues[1]) }
            if (CODE_MAIL.containsMatchIn(all)) BARE_CODE.find(all)?.let { return Code(it.groupValues[1]) }
            ORDER.find(all)?.let { return Order(it.value) }
            if (ORDER_WORDS.containsMatchIn(all)) return PurchaseHistory
            return App
        }
    }
}
