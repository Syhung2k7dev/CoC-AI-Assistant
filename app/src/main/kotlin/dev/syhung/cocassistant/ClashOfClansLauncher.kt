package dev.syhung.cocassistant

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/**
 * Discovers and launches the global Clash of Clans Android application.
 * Package visibility is declared in AndroidManifest.xml for Android 11+.
 */
object ClashOfClansLauncher {
    const val PACKAGE_NAME = "com.supercell.clashofclans"

    fun inspect(context: Context): String {
        val packageManager = context.packageManager
        val appInfo = try {
            packageManager.getApplicationInfo(PACKAGE_NAME, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return "Không tìm thấy Clash of Clans trên hồ sơ Android hiện tại. Hãy cài game trong cùng hồ sơ người dùng với CoC-AI-Assistant."
        } catch (error: RuntimeException) {
            return "Không thể kiểm tra Clash of Clans: ${error.message ?: "lỗi PackageManager"}"
        }

        if (!appInfo.enabled) {
            return "Clash of Clans đã được cài nhưng đang bị vô hiệu hóa. Hãy bật lại game trong Cài đặt Android."
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            ?: return "Đã tìm thấy gói Clash of Clans nhưng Android không cung cấp màn hình khởi chạy. Hãy cập nhật hoặc cài đặt lại game chính thức."

        return if (launchIntent.component != null) {
            "Đã tìm thấy Clash of Clans và màn hình khởi chạy hợp lệ."
        } else {
            "Đã tìm thấy Clash of Clans, nhưng thông tin màn hình khởi chạy chưa đầy đủ."
        }
    }

    fun launch(context: Context): String {
        val packageManager = context.packageManager
        val appInfo = try {
            packageManager.getApplicationInfo(PACKAGE_NAME, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            return "Không thể mở CoC: không tìm thấy com.supercell.clashofclans trên hồ sơ Android hiện tại."
        } catch (error: RuntimeException) {
            return "Không thể kiểm tra CoC: ${error.message ?: "lỗi PackageManager"}"
        }

        if (!appInfo.enabled) {
            return "Không thể mở CoC: ứng dụng đang bị vô hiệu hóa trong Android."
        }

        val launchIntent = packageManager.getLaunchIntentForPackage(PACKAGE_NAME)
            ?: return "Đã tìm thấy CoC nhưng không có Launch Intent. Hãy mở game thủ công hoặc cài lại bản chính thức."

        return try {
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launchIntent)
            "Android đã nhận lệnh mở Clash of Clans. Khi game hiện lên, quay lại CoC-AI-Assistant để tiếp tục."
        } catch (_: ActivityNotFoundException) {
            "Android không tìm thấy Activity khởi chạy CoC. Hãy cập nhật hoặc cài lại game."
        } catch (_: SecurityException) {
            "Android từ chối mở CoC do giới hạn quyền hoặc hồ sơ ứng dụng."
        } catch (error: RuntimeException) {
            "Mở CoC thất bại: ${error.message ?: error.javaClass.simpleName}"
        }
    }
}
