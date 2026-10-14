package com.posapp.pro

import android.content.Context
import android.util.Base64
import androidx.room.*
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

fun Long.vnd(): String = String.format(Locale("vi", "VN"), "%,d đ", this)

@Entity(tableName = "users") data class User(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val salt: String, val hash: String, val role: String)
@Entity(tableName = "categories") data class Category(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val sort: Int = 0)
@Entity(tableName = "items") data class Item(@PrimaryKey(autoGenerate = true) val id: Long = 0, val categoryId: Long, val name: String, val price: Long, val active: Boolean = true)
@Entity(tableName = "dtables") data class DTable(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val area: String = "")
@Entity(tableName = "orders") data class Order(@PrimaryKey(autoGenerate = true) val id: Long = 0, val tableId: Long, val status: String, val createdAt: Long, val staff: String, val paidAt: Long = 0, val discount: Long = 0, val paidMethod: String = "", val cashGiven: Long = 0, val seq: Long = 0)
@Entity(tableName = "lines") data class Line(@PrimaryKey(autoGenerate = true) val id: Long = 0, val orderId: Long, val itemId: Long, val name: String, val price: Long, val qty: Int, val note: String = "")
@Entity(tableName = "return_log") data class ReturnLog(@PrimaryKey(autoGenerate = true) val id: Long = 0, val orderId: Long, val lineName: String, val qty: Int, val amount: Long, val reason: String, val staff: String, val time: Long)
@Entity(tableName = "settings") data class Setting(@PrimaryKey val k: String, val v: String)

data class Sold(val name: String, val qty: Int, val amount: Long)

@Dao interface PosDao {
    @Query("SELECT COUNT(*) FROM users") fun userCount(): Int
    @Query("SELECT * FROM users ORDER BY name") fun users(): List<User>
    @Insert fun insertUser(u: User): Long
    @Update fun updateUser(u: User)
    @Query("DELETE FROM users WHERE id=:id") fun delUser(id: Long)

    @Query("SELECT * FROM categories ORDER BY sort,id") fun cats(): List<Category>
    @Insert fun insertCat(c: Category): Long
    @Query("DELETE FROM categories WHERE id=:id") fun delCat(id: Long)
    @Query("DELETE FROM items WHERE categoryId=:id") fun delItemsOf(id: Long)
    @Query("SELECT * FROM items WHERE categoryId=:c ORDER BY name") fun items(c: Long): List<Item>
    @Query("SELECT * FROM items WHERE active=1 ORDER BY name") fun activeItems(): List<Item>
    @Insert fun insertItem(i: Item): Long
    @Update fun updateItem(i: Item)
    @Query("DELETE FROM items WHERE id=:id") fun delItem(id: Long)

    @Query("SELECT * FROM dtables ORDER BY area,id") fun tables(): List<DTable>
    @Insert fun insertTable(t: DTable): Long
    @Query("DELETE FROM dtables WHERE id=:id") fun delTable(id: Long)
    @Query("SELECT name FROM dtables WHERE id=:id") fun tableName(id: Long): String?

    @Query("SELECT * FROM orders WHERE tableId=:t AND status='OPEN' LIMIT 1") fun openOrder(t: Long): Order?
    @Query("SELECT DISTINCT tableId FROM orders WHERE status='OPEN'") fun busy(): List<Long>
    @Query("SELECT * FROM orders WHERE id=:id") fun getOrder(id: Long): Order?
    @Insert fun insertOrder(o: Order): Long
    @Update fun updateOrder(o: Order)
    @Query("SELECT COALESCE(MAX(seq),0) FROM orders") fun maxSeq(): Long
    @Query("SELECT * FROM orders WHERE status='PAID' ORDER BY paidAt DESC LIMIT 200") fun paid(): List<Order>
    @Query("SELECT COALESCE(SUM((SELECT COALESCE(SUM(price*qty),0) FROM lines WHERE orderId=o.id)-o.discount),0) FROM orders o WHERE status='PAID' AND paidAt>=:from") fun revenue(from: Long): Long
    @Query("SELECT COUNT(*) FROM orders WHERE status='PAID' AND paidAt>=:from") fun paidCount(from: Long): Int

    @Query("SELECT * FROM lines WHERE orderId=:o ORDER BY id") fun lines(o: Long): List<Line>
    @Query("SELECT * FROM lines WHERE orderId=:o AND itemId=:i AND note='' LIMIT 1") fun lineOf(o: Long, i: Long): Line?
    @Query("SELECT COALESCE(SUM(price*qty),0) FROM lines WHERE orderId=:o") fun sub(o: Long): Long
    @Insert fun insertLine(l: Line): Long
    @Query("UPDATE lines SET qty=qty+:d WHERE id=:id AND qty+:d>=1") fun addQty(id: Long, d: Int)
    @Query("UPDATE lines SET qty=:q WHERE id=:id") fun setQty(id: Long, q: Int)
    @Query("DELETE FROM lines WHERE id=:id") fun delLine(id: Long)

    @Insert fun insertReturn(r: ReturnLog)
    @Query("SELECT COALESCE(SUM(qty),0) FROM return_log WHERE time>=:from") fun returnedQty(from: Long): Int
    @Query("SELECT * FROM orders WHERE status='OPEN'") fun openOrders(): List<Order>
    @Query("SELECT * FROM orders WHERE status='PAID' AND paidAt>=:s AND paidAt<:e ORDER BY paidAt") fun paidBetween(s: Long, e: Long): List<Order>
    @Query("SELECT * FROM return_log WHERE time>=:s AND time<:e ORDER BY time") fun returnsBetween(s: Long, e: Long): List<ReturnLog>
    @Query("SELECT * FROM return_log ORDER BY time DESC LIMIT 200") fun returns(): List<ReturnLog>
    @Query("SELECT l.name AS name, SUM(l.qty) AS qty, SUM(l.price*l.qty) AS amount FROM lines l JOIN orders o ON o.id=l.orderId WHERE o.status='PAID' AND o.paidAt>=:s AND o.paidAt<:e GROUP BY l.name ORDER BY qty DESC LIMIT 10") fun sold(s: Long, e: Long): List<Sold>

    @Query("SELECT v FROM settings WHERE k=:k") fun setting(k: String): String?
    @Query("SELECT * FROM settings") fun allSettings(): List<Setting>
    @Insert(onConflict = OnConflictStrategy.REPLACE) fun putSetting(s: Setting)
}

@Database(entities = [User::class, Category::class, Item::class, DTable::class, Order::class, Line::class, ReturnLog::class, Setting::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): PosDao
    companion object {
        @Volatile private var inst: AppDb? = null
        fun get(c: Context): AppDb = inst ?: synchronized(this) {
            inst ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "pos.db").build().also { inst = it }
        }
    }
}

object Sec {
    fun salt(): ByteArray = ByteArray(16).also { SecureRandom().nextBytes(it) }
    fun hash(pw: String, salt: ByteArray): String {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1")
        return Base64.encodeToString(f.generateSecret(PBEKeySpec(pw.toCharArray(), salt, 10000, 256)).encoded, Base64.NO_WRAP)
    }
    fun make(name: String, pw: String, role: String): User {
        val s = salt()
        return User(name = name, salt = Base64.encodeToString(s, Base64.NO_WRAP), hash = hash(pw, s), role = role)
    }
    fun check(pw: String, u: User) = hash(pw, Base64.decode(u.salt, Base64.NO_WRAP)) == u.hash
}
