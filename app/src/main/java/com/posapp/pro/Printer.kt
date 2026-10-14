package com.posapp.pro

import android.content.Context
import com.sunmi.peripheral.printer.InnerPrinterCallback
import com.sunmi.peripheral.printer.InnerPrinterManager
import com.sunmi.peripheral.printer.SunmiPrinterService
import java.text.SimpleDateFormat
import java.util.*

object Printer {
    @Volatile var svc: SunmiPrinterService? = null

    fun bind(c: Context) {
        try {
            InnerPrinterManager.getInstance().bindService(c, object : InnerPrinterCallback() {
                override fun onConnected(s: SunmiPrinterService) { svc = s }
                override fun onDisconnected() { svc = null }
            })
        } catch (e: Throwable) { }
    }

    /** Trả về null nếu gửi lệnh thành công, ngược lại là thông báo lỗi. Không tự in lại. */
    fun print(items: List<Pair<String, Int>>): String? {
        val s = svc ?: return "Chưa kết nối máy in tích hợp (chỉ có trên máy Sunmi)"
        return try {
            s.printerInit(null)
            for ((t, a) in items) { s.setAlignment(a, null); s.printText(t + "\n", null) }
            s.lineWrap(3, null)
            null
        } catch (e: Throwable) { "Lỗi máy in: " + e.message }
    }

    /** mode: 0 = hóa đơn, 1 = in lại, 2 = tạm tính */
    fun receipt(s: Map<String, String>, o: Order, ls: List<Line>, tableName: String, mode: Int): List<Pair<String, Int>> {
        val w = s["width"]?.toIntOrNull() ?: 32
        val out = mutableListOf<Pair<String, Int>>()
        fun c(t: String) { out.add(t to 1) }
        fun l(t: String) { out.add(t to 0) }
        fun lr(a: String, b: String) { l(a + " ".repeat((w - a.length - b.length).coerceAtLeast(1)) + b) }
        val sep = "-".repeat(w)
        c(s["store"] ?: "")
        s["addr"]?.takeIf { it.isNotEmpty() }?.let { c(it) }
        s["phone"]?.takeIf { it.isNotEmpty() }?.let { c("ĐT: $it") }
        c(sep)
        c(if (mode == 2) "PHIẾU TẠM TÍNH" else "HÓA ĐƠN #${o.seq}")
        if (mode == 1) c("*** BẢN IN LẠI ***")
        val f = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
        l("Ngày: " + f.format(Date(if (o.paidAt > 0) o.paidAt else System.currentTimeMillis())))
        l("Bàn: $tableName"); l("NV: ${o.staff}"); l(sep)
        var sub = 0L
        for (x in ls) { l(x.name); lr("  ${x.qty} x ${x.price.vnd()}", (x.price * x.qty).vnd()); sub += x.price * x.qty }
        l(sep)
        lr("Tạm tính", sub.vnd())
        if (o.discount > 0) lr("Giảm giá", "-" + o.discount.vnd())
        lr("TỔNG", (sub - o.discount).vnd())
        if (mode != 2) {
            lr("Thanh toán", o.paidMethod)
            if (o.paidMethod == "Tiền mặt" && o.cashGiven > 0) {
                lr("Khách đưa", o.cashGiven.vnd()); lr("Tiền thừa", (o.cashGiven - (sub - o.discount)).vnd())
            }
        }
        l(sep); c(s["footer"] ?: "Cảm ơn quý khách!")
        return out
    }
}
