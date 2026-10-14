package com.posapp.pro

import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    lateinit var db: AppDb
    lateinit var frame: FrameLayout
    lateinit var content: FrameLayout
    val ex = Executors.newSingleThreadExecutor()
    var me: User? = null
    var store = ""
    var repP = 1
    var repOff = 0
    val PRIMARY = Color.parseColor("#1565C0"); val GREEN = Color.parseColor("#2E7D32")
    val GRAY = Color.parseColor("#78909C"); val RED = Color.parseColor("#C62828"); val ORANGE = Color.parseColor("#EF6C00")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        db = AppDb.get(this); Printer.bind(this)
        frame = FrameLayout(this); frame.setBackgroundColor(Color.parseColor("#F5F7FA"))
        setContentView(frame); start()
    }

    // ---------- helpers ----------
    fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()
    fun toast(s: String) { Toast.makeText(this, s, Toast.LENGTH_SHORT).show() }
    fun <T> bg(work: () -> T, ui: (T) -> Unit = {}) {
        ex.execute {
            try { val r = work(); runOnUiThread { ui(r) } }
            catch (e: Throwable) { runOnUiThread { toast("Lỗi: " + e.message) } }
        }
    }
    fun admin(): Boolean = me?.role == "ADMIN"
    fun canPay(): Boolean = me?.role != "WAITER"
    fun roleName(r: String): String = when (r) { "ADMIN" -> "Quản lý"; "CASHIER" -> "Thu ngân"; else -> "Phục vụ" }
    fun rect(c: Int, r: Int): GradientDrawable { val g = GradientDrawable(); g.setColor(c); g.cornerRadius = dp(r).toFloat(); return g }
    fun tv(t: String, size: Float = 16f, bold: Boolean = false, color: Int = Color.parseColor("#222222")): TextView {
        val v = TextView(this); v.text = t; v.textSize = size; v.setTextColor(color); if (bold) v.setTypeface(null, Typeface.BOLD); return v
    }
    fun btn(t: String, color: Int = PRIMARY, on: () -> Unit): Button {
        val b = Button(this); b.text = t; b.setTextColor(Color.WHITE); b.isAllCaps = false; b.background = rect(color, 10)
        b.setPadding(dp(12), dp(8), dp(12), dp(8)); b.setOnClickListener { on() }; return b
    }
    fun col(vararg v: View): LinearLayout { val l = LinearLayout(this); l.orientation = LinearLayout.VERTICAL; v.forEach { l.addView(it) }; return l }
    fun row(vararg v: View): LinearLayout { val l = LinearLayout(this); l.orientation = LinearLayout.HORIZONTAL; l.gravity = Gravity.CENTER_VERTICAL; v.forEach { l.addView(it) }; return l }
    fun pad(v: View, n: Int = 12): View { v.setPadding(dp(n), dp(n), dp(n), dp(n)); return v }
    fun View.w(f: Float = 1f): View { layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, f); return this }
    fun View.fill(): View { layoutParams = LinearLayout.LayoutParams(-1, 0, 1f); return this }
    fun scroll(v: View): ScrollView { val s = ScrollView(this); s.addView(v); return s }
    fun et(h: String, type: Int = 0, init: String = ""): EditText {
        val e = EditText(this); e.hint = h; e.setText(init)
        e.inputType = when (type) {
            1 -> InputType.TYPE_CLASS_NUMBER
            2 -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            else -> InputType.TYPE_CLASS_TEXT
        }
        return e
    }
    fun EditText.onChange(f: () -> Unit) {
        addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) { f() }
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
    }
    class F(val hint: String, val init: String = "", val type: Int = 0)
    fun form(title: String, fs: List<F>, okText: String = "Lưu", ok: (List<String>) -> Unit) {
        val ets = fs.map { et(it.hint, it.type, it.init) }
        val box = col(*ets.toTypedArray()); pad(box, 16)
        AlertDialog.Builder(this).setTitle(title).setView(box)
            .setPositiveButton(okText) { _, _ -> ok(ets.map { it.text.toString().trim() }) }
            .setNegativeButton("Hủy", null).show()
    }
    fun confirm(msg: String, ok: () -> Unit) {
        AlertDialog.Builder(this).setMessage(msg).setPositiveButton("Đồng ý") { _, _ -> ok() }.setNegativeButton("Hủy", null).show()
    }
    fun show(v: View) { frame.removeAllViews(); frame.addView(v) }
    fun tab(v: View) { content.removeAllViews(); content.addView(v) }
    fun settings(): Map<String, String> = db.dao().allSettings().associate { it.k to it.v }
    fun card(title: String, value: String): View {
        val c = pad(col(tv(title, 12f), tv(value, 18f, true)), 12)
        c.background = rect(Color.WHITE, 12)
        val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f); lp.setMargins(dp(4), dp(4), dp(4), dp(4))
        c.layoutParams = lp; return c
    }

    // ---------- thiết lập / đăng nhập ----------
    fun start() { bg({ db.dao().userCount() }) { if (it == 0) setupScreen() else loginScreen() } }

    fun setupScreen() {
        val a = et("Tên cửa hàng"); val n = et("Tên quản lý"); val p = et("Mật khẩu (tối thiểu 4 ký tự)", 2)
        show(pad(col(tv("Thiết lập lần đầu", 22f, true), a, n, p, btn("Tạo cửa hàng") {
            val s = a.text.toString().trim(); val nm = n.text.toString().trim(); val pw = p.text.toString()
            if (s.isEmpty() || nm.isEmpty() || pw.length < 4) toast("Nhập đủ thông tin, mật khẩu tối thiểu 4 ký tự")
            else bg({ db.dao().putSetting(Setting("store", s)); db.dao().insertUser(Sec.make(nm, pw, "ADMIN")) }) { loginScreen() }
        }), 24))
    }

    fun loginScreen() {
        bg({ db.dao().users() to (db.dao().setting("store") ?: "") }) { (us, st) ->
            store = st; me = null
            val sp = Spinner(this)
            sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, us.map { it.name })
            val p = et("Mật khẩu", 2)
            show(pad(col(tv(store.ifEmpty { "POS" }, 24f, true), tv("Đăng nhập"), sp, p, btn("Đăng nhập") {
                val u = us[sp.selectedItemPosition]
                if (Sec.check(p.text.toString(), u)) { me = u; mainScreen() } else toast("Sai mật khẩu")
            }), 24))
        }
    }

    // ---------- khung chính: thanh menu bên trái ----------
    fun mainScreen() {
        content = FrameLayout(this)
        val navs = mutableListOf<Triple<String, String, () -> Unit>>()
        navs.add(Triple("🪑", "Bàn", { tablesTab() }))
        if (me!!.role != "WAITER") navs.add(Triple("🧾", "Lịch sử", { historyTab(0) }))
        if (admin()) {
            navs.add(Triple("📊", "Báo cáo", { reportTab() }))
            navs.add(Triple("⚙️", "Cài đặt", { settingsTab() }))
        }
        val side = LinearLayout(this); side.orientation = LinearLayout.VERTICAL; side.setBackgroundColor(Color.parseColor("#0D47A1"))
        val views = mutableListOf<TextView>()
        fun select(k: Int) { views.forEachIndexed { j, v -> v.setBackgroundColor(if (j == k) PRIMARY else Color.TRANSPARENT) } }
        navs.forEachIndexed { k, nav ->
            val v = tv(nav.first + "\n" + nav.second, 12f, true, Color.WHITE)
            v.gravity = Gravity.CENTER; v.setPadding(0, dp(14), 0, dp(14))
            v.setOnClickListener { select(k); nav.third() }
            views.add(v); side.addView(v, LinearLayout.LayoutParams(-1, -2))
        }
        side.addView(View(this), LinearLayout.LayoutParams(-1, 0, 1f))
        val who = tv(me!!.name, 11f, false, Color.WHITE); who.gravity = Gravity.CENTER
        side.addView(who, LinearLayout.LayoutParams(-1, -2))
        val out = tv("⏻\nThoát", 12f, true, Color.WHITE); out.gravity = Gravity.CENTER; out.setPadding(0, dp(12), 0, dp(14))
        out.setOnClickListener { confirm("Đăng xuất?") { loginScreen() } }
        side.addView(out, LinearLayout.LayoutParams(-1, -2))
        val header = tv(store, 18f, true, Color.WHITE); header.setBackgroundColor(PRIMARY); header.setPadding(dp(14), dp(12), dp(14), dp(12))
        val right = col(header, content.fill())
        val root = LinearLayout(this); root.orientation = LinearLayout.HORIZONTAL
        root.addView(side, LinearLayout.LayoutParams(dp(76), -1))
        root.addView(right, LinearLayout.LayoutParams(0, -1, 1f))
        show(root); select(0); tablesTab()
    }

    // ---------- bàn ----------
    fun tablesTab() {
        bg({
            val d = db.dao()
            val info = HashMap<Long, Triple<Int, Long, Long>>()
            for (o in d.openOrders()) { val ls = d.lines(o.id); info[o.tableId] = Triple(ls.sumOf { it.qty }, ls.sumOf { it.price * it.qty }, o.createdAt) }
            d.tables() to info
        }) { (ts, info) ->
            val grid = GridLayout(this); grid.columnCount = 2
            ts.forEach { t ->
                val inf = info[t.id]
                var label = t.name + (if (t.area.isNotEmpty()) "\n" + t.area else "")
                if (inf != null) label += "\nKhách đang ăn\n${inf.first} món • ${inf.second.vnd()}\n${(System.currentTimeMillis() - inf.third) / 60000} phút"
                val cell = tv(label, 16f, true, Color.WHITE)
                cell.gravity = Gravity.CENTER; cell.background = rect(if (inf != null) ORANGE else GRAY, 14)
                cell.setOnClickListener { tableScreen(t) }
                val lp = GridLayout.LayoutParams(GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f))
                lp.width = 0; lp.height = dp(110); lp.setMargins(dp(6), dp(6), dp(6), dp(6))
                grid.addView(cell, lp)
            }
            val hint = if (ts.isEmpty()) "Chưa có bàn. Vào Cài đặt > Quản lý bàn để thêm." else "Xám: bàn trống • Cam: khách đang ăn (chờ thanh toán)"
            tab(col(pad(tv(hint, 12f), 8), scroll(grid).fill()))
        }
    }

    fun tableScreen(t: DTable) {
        bg({ val d = db.dao(); val o = d.openOrder(t.id); o to (if (o != null) d.lines(o.id) else emptyList<Line>()) }) { (o, ls) ->
            if (o == null) pickScreen(t, null) else occupiedScreen(t, o, ls)
        }
    }

    // bàn trống / gọi thêm: chọn món, danh mục bên trái
    fun pickScreen(t: DTable, existing: Order?) {
        bg({ db.dao().cats() to db.dao().activeItems() }) { (cats, items) ->
            val cart = LinkedHashMap<Long, Int>()
            var selCat = 0L
            val itemsBox = col(); val catBox = col()
            val search = et("Tìm món...")
            val totalTv = tv("0 món", 17f, true)
            fun refreshBar() {
                var n = 0; var tot = 0L
                for ((id, q) in cart) { val it2 = items.first { x -> x.id == id }; n += q; tot += it2.price * q }
                totalTv.text = "$n món • ${tot.vnd()}"
            }
            fun renderItems() {
                itemsBox.removeAllViews()
                val q = search.text.toString()
                val shown = items.filter { (selCat == 0L || it.categoryId == selCat) && it.name.contains(q, true) }
                if (shown.isEmpty()) itemsBox.addView(pad(tv("Chưa có món. Vào Cài đặt > Thực đơn để thêm.", 14f), 12))
                shown.forEach { i ->
                    val qty = cart[i.id] ?: 0
                    val ctrl: View = if (qty == 0) btn("+", GREEN) { cart[i.id] = 1; renderItems(); refreshBar() }
                    else row(
                        btn("−", GRAY) { val n = (cart[i.id] ?: 1) - 1; if (n <= 0) cart.remove(i.id) else cart[i.id] = n; renderItems(); refreshBar() },
                        pad(tv("$qty", 18f, true), 8),
                        btn("+", GREEN) { cart[i.id] = qty + 1; renderItems(); refreshBar() })
                    itemsBox.addView(pad(row(col(tv(i.name, 16f, true), tv(i.price.vnd(), 13f)).w(1f), ctrl), 8))
                }
            }
            fun renderCats() {
                catBox.removeAllViews()
                val all = listOf(Category(id = 0, name = "Tất cả")) + cats
                all.forEach { c ->
                    val v = tv(c.name, 13f, true, if (c.id == selCat) Color.WHITE else Color.parseColor("#222222"))
                    v.gravity = Gravity.CENTER; v.setPadding(dp(4), dp(14), dp(4), dp(14))
                    v.setBackgroundColor(if (c.id == selCat) PRIMARY else Color.TRANSPARENT)
                    v.setOnClickListener { selCat = c.id; renderCats(); renderItems() }
                    catBox.addView(v, LinearLayout.LayoutParams(-1, -2))
                }
            }
            search.onChange { renderItems() }
            val ok = btn(if (existing == null) "Gọi món" else "Gọi thêm", GREEN) {
                if (cart.isEmpty()) toast("Chưa chọn món")
                else bg({
                    db.runInTransaction {
                        val d = db.dao()
                        val oid = existing?.id ?: d.openOrder(t.id)?.id ?: d.insertOrder(Order(tableId = t.id, status = "OPEN", createdAt = System.currentTimeMillis(), staff = me?.name ?: ""))
                        for ((id, q) in cart) {
                            val item = items.first { x -> x.id == id }
                            val old = d.lineOf(oid, id)
                            if (old != null) d.addQty(old.id, q) else d.insertLine(Line(orderId = oid, itemId = id, name = item.name, price = item.price, qty = q))
                        }
                    }
                }) { toast("Đã gọi món cho " + t.name); tablesTab() }
            }
            val left = scroll(catBox); left.layoutParams = LinearLayout.LayoutParams(dp(88), -1)
            val right = col(pad(search, 6), scroll(itemsBox).fill()); right.layoutParams = LinearLayout.LayoutParams(0, -1, 1f)
            val body = row(left, right)
            renderCats(); renderItems(); refreshBar()
            tab(col(row(btn("← Bàn") { tablesTab() }, tv("  " + t.name + (if (existing != null) " • gọi thêm" else ""), 17f, true)),
                body.fill(), pad(row(totalTv.w(1f), ok), 8)))
        }
    }

    // bàn đang có khách: chỉ trả món và thanh toán
    fun occupiedScreen(t: DTable, o: Order, ls: List<Line>) {
        val total = ls.sumOf { it.price * it.qty }
        val list = col()
        ls.forEach { l ->
            list.addView(pad(row(
                col(tv(l.name, 16f, true), tv("${l.price.vnd()} x ${l.qty}", 13f)).w(1f),
                tv((l.price * l.qty).vnd(), 15f, true),
                btn("Trả", RED) { returnDialog(t, o, l) }
            ), 8))
        }
        val bar = col(
            tv("Tổng: " + total.vnd(), 20f, true),
            row(btn("Gọi thêm", GRAY) { pickScreen(t, o) }.w(1f), btn("In tạm", GRAY) { printOrder(o, ls, 2) }.w(1f)),
            btn("Thanh toán", GREEN) { if (!canPay()) toast("Bạn không có quyền thanh toán") else payDialog(t, o, ls) }
        )
        tab(col(row(btn("← Bàn") { tablesTab() }, tv("  ${t.name} • Khách đang ăn", 16f, true)), scroll(list).fill(), pad(bar, 8)))
    }

    fun returnDialog(t: DTable, o: Order, l: Line) {
        if (!canPay()) { toast("Bạn không có quyền trả món"); return }
        form("Trả món: ${l.name} (có ${l.qty})", listOf(F("Số lượng trả", "1", 1), F("Lý do", "Khách trả")), "Xác nhận") { v ->
            val q = v[0].toIntOrNull() ?: 0
            if (q < 1 || q > l.qty) toast("Số lượng không hợp lệ")
            else bg({
                db.runInTransaction<Boolean> {
                    val d = db.dao(); val cur = d.getOrder(o.id)
                    if (cur == null || cur.status != "OPEN") false
                    else {
                        if (q == l.qty) d.delLine(l.id) else d.setQty(l.id, l.qty - q)
                        d.insertReturn(ReturnLog(orderId = o.id, lineName = l.name, qty = q, amount = l.price * q, reason = v[1].ifEmpty { "Không ghi" }, staff = me?.name ?: "", time = System.currentTimeMillis()))
                        if (d.lines(o.id).isEmpty()) { d.updateOrder(cur.copy(status = "CANCELLED")); true } else false
                    }
                }
            }) { closed -> if (closed) { toast("Đã trả hết món, bàn trống"); tablesTab() } else tableScreen(t) }
        }
    }

    // ---------- thanh toán ----------
    fun payDialog(t: DTable, o: Order, ls: List<Line>) {
        val sub = ls.sumOf { it.price * it.qty }
        val disc = et("Giảm giá (đ)", 1); val given = et("Khách đưa (đ) - tiền mặt", 1)
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, listOf("Tiền mặt", "Chuyển khoản", "QR / Thẻ"))
        val info = tv("", 16f, true)
        fun dd(): Long = (disc.text.toString().toLongOrNull() ?: 0L).coerceIn(0L, sub)
        fun calc() {
            val tot = sub - dd(); val g = given.text.toString().toLongOrNull() ?: 0L
            info.text = "Tạm tính: ${sub.vnd()}\nTổng thanh toán: ${tot.vnd()}" + (if (sp.selectedItemPosition == 0 && g > 0) "\nTiền thừa: ${(g - tot).vnd()}" else "")
        }
        disc.onChange { calc() }; given.onChange { calc() }; calc()
        val box = col(info, disc, sp, given, tv("Chuyển khoản/QR: chỉ bấm xác nhận khi đã NHẬN được tiền.", 12f)); pad(box, 16)
        AlertDialog.Builder(this).setTitle("Thanh toán " + t.name).setView(box)
            .setPositiveButton("Xác nhận đã nhận tiền") { _, _ ->
                val method = listOf("Tiền mặt", "Chuyển khoản", "QR / Thẻ")[sp.selectedItemPosition]
                val d0 = dd(); val tot = sub - d0; val g = given.text.toString().toLongOrNull() ?: 0L
                if (method == "Tiền mặt" && g > 0 && g < tot) { toast("Khách đưa chưa đủ tiền"); payDialog(t, o, ls) }
                else bg({
                    db.runInTransaction<Order?> {
                        val d = db.dao(); val cur = d.getOrder(o.id)
                        if (cur == null || cur.status != "OPEN") null
                        else cur.copy(status = "PAID", paidAt = System.currentTimeMillis(), discount = d0, paidMethod = method, cashGiven = if (method == "Tiền mặt") g else 0L, seq = d.maxSeq() + 1).also { d.updateOrder(it) }
                    }
                }) { paid ->
                    if (paid == null) toast("Đơn đã được thanh toán trước đó")
                    else { toast("Thanh toán thành công"); bg({ settings()["auto"] != "0" }) { a -> if (a) printOrder(paid, ls, 0) }; tablesTab() }
                }
            }.setNegativeButton("Hủy", null).show()
    }

    fun printOrder(o: Order, ls: List<Line>, mode: Int) {
        bg({ Printer.print(Printer.receipt(settings(), o, ls, db.dao().tableName(o.tableId) ?: "?", mode)) }) { err -> toast(err ?: "Đã gửi lệnh in") }
    }

    // ---------- lịch sử ----------
    fun historyTab(mode: Int) {
        bg({
            val d = db.dao()
            Triple(d.paid().map { it to (d.sub(it.id) - it.discount) }, d.returns(), d.tables().associate { it.id to it.name })
        }) { (orders, rets, tn) ->
            val list = col(); val f = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault())
            if (mode == 0) {
                if (orders.isEmpty()) list.addView(pad(tv("Chưa có hóa đơn.", 14f), 12))
                orders.forEach { (o, total) ->
                    val v = tv("#${o.seq} • ${f.format(Date(o.paidAt))} • ${tn[o.tableId] ?: "?"}\n${total.vnd()} • ${o.paidMethod} • ${o.staff}", 14f)
                    v.setOnClickListener { detailDialog(o, total) }; list.addView(pad(v, 10))
                }
            } else {
                if (rets.isEmpty()) list.addView(pad(tv("Chưa có món bị trả/hủy.", 14f), 12))
                rets.forEach { r -> list.addView(pad(tv("${f.format(Date(r.time))} • ${r.qty} x ${r.lineName} = ${r.amount.vnd()}\n${r.reason} • ${r.staff}", 14f), 10)) }
            }
            val seg = row(btn("Hóa đơn", if (mode == 0) PRIMARY else GRAY) { historyTab(0) }.w(1f), btn("Trả/hủy món", if (mode == 1) PRIMARY else GRAY) { historyTab(1) }.w(1f))
            tab(col(pad(seg, 6), scroll(list).fill()))
        }
    }

    fun detailDialog(o: Order, total: Long) {
        bg({ db.dao().lines(o.id) }) { ls ->
            val txt = ls.joinToString("\n") { "${it.qty} x ${it.name} = ${(it.price * it.qty).vnd()}" } +
                (if (o.discount > 0) "\nGiảm giá: ${o.discount.vnd()}" else "") + "\nTổng: ${total.vnd()}\nNV: ${o.staff} • ${o.paidMethod}"
            AlertDialog.Builder(this).setTitle("Hóa đơn #${o.seq}").setMessage(txt)
                .setPositiveButton("In lại") { _, _ -> printOrder(o, ls, 1) }.setNegativeButton("Đóng", null).show()
        }
    }

    // ---------- báo cáo: ngày / tuần / tháng / năm ----------
    fun range(p: Int, off: Int): Triple<Long, Long, String> {
        val c = Calendar.getInstance(); c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        val f = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        when (p) {
            0 -> c.add(Calendar.DAY_OF_YEAR, off)
            1 -> { c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); c.add(Calendar.WEEK_OF_YEAR, off) }
            2 -> { c.set(Calendar.DAY_OF_MONTH, 1); c.add(Calendar.MONTH, off) }
            else -> { c.set(Calendar.DAY_OF_YEAR, 1); c.add(Calendar.YEAR, off) }
        }
        val s = c.timeInMillis
        when (p) {
            0 -> c.add(Calendar.DAY_OF_YEAR, 1)
            1 -> c.add(Calendar.DAY_OF_YEAR, 7)
            2 -> c.add(Calendar.MONTH, 1)
            else -> c.add(Calendar.YEAR, 1)
        }
        val e = c.timeInMillis
        val label = when (p) {
            0 -> f.format(Date(s))
            1 -> f.format(Date(s)) + " - " + f.format(Date(e - 1))
            2 -> "Tháng " + SimpleDateFormat("MM/yyyy", Locale.getDefault()).format(Date(s))
            else -> "Năm " + SimpleDateFormat("yyyy", Locale.getDefault()).format(Date(s))
        }
        return Triple(s, e, label)
    }

    fun reportTab() {
        bg({
            val d = db.dao(); val r = range(repP, repOff)
            val os = d.paidBetween(r.first, r.second).map { it to (d.sub(it.id) - it.discount) }
            Triple(r.third, os, d.returnsBetween(r.first, r.second)) to d.sold(r.first, r.second)
        }) { (a, sold) ->
            val label = a.first; val os = a.second; val rs = a.third
            val revenue = os.sumOf { it.second }
            val discount = os.sumOf { it.first.discount }
            val avg = if (os.isEmpty()) 0L else revenue / os.size
            val box = col()
            val names = listOf("Ngày", "Tuần", "Tháng", "Năm")
            val per = row(*names.mapIndexed { i, n -> btn(n, if (i == repP) PRIMARY else GRAY) { repP = i; repOff = 0; reportTab() }.w(1f) }.toTypedArray())
            val nav = row(btn("◀") { repOff--; reportTab() }, pad(tv(label, 15f, true), 8).w(1f), btn("▶") { if (repOff < 0) { repOff++; reportTab() } })
            box.addView(pad(per, 6)); box.addView(pad(nav, 6))
            box.addView(row(card("Doanh thu (thực thu)", revenue.vnd()), card("Số đơn", "${os.size}")))
            box.addView(row(card("Trung bình/đơn", avg.vnd()), card("Giảm giá", discount.vnd())))
            box.addView(row(card("Món trả/hủy", "${rs.sumOf { it.qty }}"), card("Giá trị trả/hủy", rs.sumOf { it.amount }.vnd())))
            box.addView(pad(tv("Theo phương thức thanh toán", 14f, true), 10))
            if (os.isEmpty()) box.addView(pad(tv("Không có giao dịch trong kỳ này.", 13f), 10))
            os.groupBy { it.first.paidMethod }.forEach { (m, v) -> box.addView(pad(tv("$m: ${v.size} đơn • ${v.sumOf { x -> x.second }.vnd()}", 14f), 6)) }
            if (repP != 0 && os.isNotEmpty()) {
                box.addView(pad(tv(if (repP == 3) "Theo tháng" else "Theo ngày", 14f, true), 10))
                val kf = SimpleDateFormat(if (repP == 3) "MM/yyyy" else "dd/MM", Locale.getDefault())
                os.groupBy { kf.format(Date(it.first.paidAt)) }.forEach { (k, v) -> box.addView(pad(tv("$k: ${v.size} đơn • ${v.sumOf { x -> x.second }.vnd()}", 14f), 6)) }
            }
            if (sold.isNotEmpty()) {
                box.addView(pad(tv("Món bán chạy", 14f, true), 10))
                sold.forEach { s -> box.addView(pad(tv("${s.name}: ${s.qty} • ${s.amount.vnd()}", 14f), 6)) }
            }
            tab(scroll(box))
        }
    }

    // ---------- cài đặt (tất cả gom một mục) ----------
    fun sub(title: String, v: View) {
        tab(col(row(btn("← Cài đặt") { settingsTab() }, tv("  $title", 17f, true)), v.fill()))
    }

    fun settingsTab() {
        val items = listOf<Pair<String, () -> Unit>>(
            "🏪  Thông tin cửa hàng & hóa đơn" to { storePage() },
            "🖨  Máy in" to { printerPage() },
            "🪑  Quản lý bàn" to { tablesAdmin() },
            "🍽  Thực đơn (danh mục & món)" to { menuAdmin() },
            "👤  Nhân viên" to { staffPage() },
            "🔑  Đổi mật khẩu của tôi" to { passPage() }
        )
        val box = col()
        items.forEach { (n, f) ->
            val b = btn(n, PRIMARY) { f() }; b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            val lp = LinearLayout.LayoutParams(-1, dp(56)); lp.setMargins(dp(10), dp(6), dp(10), dp(6)); box.addView(b, lp)
        }
        tab(scroll(box))
    }

    fun storePage() {
        bg({ settings() }) { s ->
            val a = et("Tên cửa hàng", 0, s["store"] ?: ""); val b = et("Địa chỉ", 0, s["addr"] ?: "")
            val c = et("Số điện thoại", 0, s["phone"] ?: ""); val d = et("Lời cảm ơn cuối hóa đơn", 0, s["footer"] ?: "Cảm ơn quý khách!")
            sub("Cửa hàng", scroll(pad(col(a, b, c, d, btn("Lưu") {
                val v = listOf(a, b, c, d).map { it.text.toString().trim() }
                bg({ val x = db.dao(); x.putSetting(Setting("store", v[0])); x.putSetting(Setting("addr", v[1])); x.putSetting(Setting("phone", v[2])); x.putSetting(Setting("footer", v[3])) }) { store = v[0]; toast("Đã lưu") }
            }), 14)))
        }
    }

    fun printerPage() {
        bg({ settings() }) { s ->
            val w = et("Ký tự mỗi dòng (32 = giấy 58mm, 48 = 80mm)", 1, s["width"] ?: "32")
            val au = et("Tự in sau thanh toán (1 = có, 0 = không)", 1, s["auto"] ?: "1")
            sub("Máy in", scroll(pad(col(w, au, btn("Lưu") {
                bg({ db.dao().putSetting(Setting("width", w.text.toString().ifEmpty { "32" })); db.dao().putSetting(Setting("auto", au.text.toString().ifEmpty { "1" })) }) { toast("Đã lưu") }
            }, btn("In thử", GRAY) {
                bg({ Printer.print(listOf("IN THU" to 1, "POS Pro - Cà phê sữa đá" to 0)) }) { err -> toast(err ?: "Đã gửi lệnh in thử") }
            }), 14)))
        }
    }

    fun tablesAdmin() {
        bg({ db.dao().tables() to db.dao().busy().toSet() }) { (ts, busy) ->
            val box = col()
            box.addView(row(btn("+ Thêm bàn") {
                form("Bàn mới", listOf(F("Tên/số bàn"), F("Khu vực / tầng (tùy chọn)"))) { v ->
                    if (v[0].isNotEmpty()) bg({ db.dao().insertTable(DTable(name = v[0], area = v[1])) }) { tablesAdmin() }
                }
            }.w(1f), btn("+ Thêm nhanh nhiều bàn", GRAY) {
                form("Thêm nhanh", listOf(F("Số bàn cần thêm", "", 1), F("Khu vực / tầng (tùy chọn)"))) { v ->
                    val n = v[0].toIntOrNull() ?: 0
                    if (n < 1 || n > 100) toast("Nhập từ 1 đến 100")
                    else bg({ for (k in 1..n) db.dao().insertTable(DTable(name = "Bàn " + (ts.size + k), area = v[1])) }) { tablesAdmin() }
                }
            }.w(1f)))
            ts.forEach { t ->
                box.addView(pad(row(tv(t.name + (if (t.area.isNotEmpty()) "  (" + t.area + ")" else ""), 16f).w(1f), btn("Xóa", RED) {
                    if (busy.contains(t.id)) toast("Bàn đang có khách, không thể xóa")
                    else confirm("Xóa ${t.name}?") { bg({ db.dao().delTable(t.id) }) { tablesAdmin() } }
                }), 8))
            }
            sub("Quản lý bàn", scroll(box))
        }
    }

    fun menuAdmin() {
        bg({ val d = db.dao(); d.cats().map { it to d.items(it.id) } }) { data ->
            val box = col()
            box.addView(row(btn("+ Danh mục") {
                form("Danh mục mới", listOf(F("Tên danh mục"))) { v -> if (v[0].isNotEmpty()) bg({ db.dao().insertCat(Category(name = v[0], sort = data.size)) }) { menuAdmin() } }
            }.w(1f), btn("+ Món") {
                if (data.isEmpty()) toast("Tạo danh mục trước")
                else AlertDialog.Builder(this).setTitle("Chọn danh mục").setItems(data.map { it.first.name }.toTypedArray()) { _, k ->
                    form("Món mới", listOf(F("Tên món"), F("Giá (đ)", "", 1))) { v ->
                        val p = v[1].toLongOrNull()
                        if (v[0].isEmpty() || p == null) toast("Nhập tên và giá hợp lệ") else bg({ db.dao().insertItem(Item(categoryId = data[k].first.id, name = v[0], price = p)) }) { menuAdmin() }
                    }
                }.show()
            }.w(1f)))
            box.addView(pad(tv("Chạm món để sửa • giữ lâu để xóa", 12f), 8))
            data.forEach { (c, items) ->
                box.addView(pad(row(tv(c.name, 18f, true, PRIMARY).w(1f), btn("Xóa", RED) {
                    confirm("Xóa danh mục ${c.name} và các món trong đó?") { bg({ db.dao().delItemsOf(c.id); db.dao().delCat(c.id) }) { menuAdmin() } }
                }), 8))
                items.forEach { i ->
                    val v = tv(i.name + " — " + i.price.vnd() + (if (!i.active) "  (ẩn)" else ""), 15f)
                    v.setOnClickListener {
                        form("Sửa món", listOf(F("Tên món", i.name), F("Giá (đ)", i.price.toString(), 1), F("Hiển thị: 1 = hiện, 0 = ẩn", if (i.active) "1" else "0", 1))) { r ->
                            val p = r[1].toLongOrNull()
                            if (r[0].isEmpty() || p == null) toast("Nhập tên và giá hợp lệ") else bg({ db.dao().updateItem(i.copy(name = r[0], price = p, active = r[2] != "0")) }) { menuAdmin() }
                        }
                    }
                    v.setOnLongClickListener { confirm("Xóa món ${i.name}?") { bg({ db.dao().delItem(i.id) }) { menuAdmin() } }; true }
                    box.addView(pad(v, 10))
                }
            }
            sub("Thực đơn", scroll(box))
        }
    }

    fun staffPage() {
        bg({ db.dao().users() }) { us ->
            val box = col()
            box.addView(btn("+ Thêm nhân viên") {
                form("Nhân viên mới", listOf(F("Tên đăng nhập"), F("Mật khẩu (≥ 4 ký tự)", "", 2))) { v ->
                    if (v[0].isEmpty() || v[1].length < 4) toast("Nhập tên và mật khẩu ≥ 4 ký tự")
                    else AlertDialog.Builder(this).setTitle("Vai trò").setItems(arrayOf("Quản lý", "Thu ngân", "Phục vụ")) { _, k ->
                        bg({ db.dao().insertUser(Sec.make(v[0], v[1], listOf("ADMIN", "CASHIER", "WAITER")[k])) }) { staffPage() }
                    }.show()
                }
            })
            us.forEach { u ->
                box.addView(pad(row(tv(u.name + " - " + roleName(u.role), 16f).w(1f), btn("Xóa", RED) {
                    if (u.id == me?.id) toast("Không thể xóa chính mình")
                    else if (u.role == "ADMIN" && us.count { it.role == "ADMIN" } <= 1) toast("Phải còn ít nhất 1 quản lý")
                    else confirm("Xóa ${u.name}?") { bg({ db.dao().delUser(u.id) }) { staffPage() } }
                }), 8))
            }
            sub("Nhân viên", scroll(box))
        }
    }

    fun passPage() {
        val a = et("Mật khẩu cũ", 2); val b = et("Mật khẩu mới (≥ 4 ký tự)", 2)
        sub("Đổi mật khẩu", scroll(pad(col(a, b, btn("Đổi mật khẩu") {
            val u = me!!
            if (!Sec.check(a.text.toString(), u)) toast("Mật khẩu cũ sai")
            else if (b.text.toString().length < 4) toast("Mật khẩu mới quá ngắn")
            else bg({ val n = Sec.make(u.name, b.text.toString(), u.role).copy(id = u.id); db.dao().updateUser(n); n }) { me = it; toast("Đã đổi") }
        }), 14)))
    }
}
