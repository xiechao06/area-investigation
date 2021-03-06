package com.puzheng.region_investigation

import android.content.Context
import android.util.DisplayMetrics
import android.widget.Toast
import com.amap.api.location.AMapLocation
import com.amap.api.location.AMapLocationClient
import com.amap.api.location.AMapLocationClientOption
import com.orhanobut.logger.Logger
import nl.komponents.kovenant.deferred
import nl.komponents.kovenant.ui.promiseOnUi
import java.io.File

val Context.pixelsPerDp: Double
    get() = resources.displayMetrics.densityDpi.toDouble() / DisplayMetrics.DENSITY_DEFAULT

fun Context.openReadableFile(dir: String, type: String? = null) = File(
        (if (isExternalStorageReadable) getExternalFilesDir(type).absolutePath
        else filesDir.absolutePath) + dir)

fun Context.openReadableFile(dir: String, filename: String, type: String? = null) = File(
        (if (isExternalStorageReadable) getExternalFilesDir(type).absolutePath
        else filesDir.absolutePath) + dir, filename)

fun Context.openWritableFile(dir: String, filename: String, type: String? = null): File {
    val absoluteDirPath = (if (isExternalStorageWritable) getExternalFilesDir(type).absolutePath
    else filesDir.absolutePath) + dir

    val absoluteDir = File(absoluteDirPath)
            if (!absoluteDir.isDirectory) {
                absoluteDir.mkdirs()
            }

    return File(absoluteDirPath, filename)
}

fun Context.toast(text: CharSequence, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, text, duration).show()
}

fun Context.toast(resId: Int, duration: Int = Toast.LENGTH_SHORT) {
    Toast.makeText(this, resId, duration).show()
}

fun Context.getLocation(default: AMapLocation? = null) = deferred<AMapLocation, Exception>().apply {
        val locationClient = AMapLocationClient(this@getLocation)
        //设置定位回调监听
        locationClient.setLocationListener({
            if (it?.errorCode == 0) {
                Logger.e(it.toStr())
                resolve(it)
            } else {
                Logger.e("定位失败, ${it.errorCode}: ${it.errorInfo}");
                if (default != null) {
                    resolve(default)
                }
            }
        })
        //初始化定位参数
        val locationOption = AMapLocationClientOption()
        //设置定位模式为高精度模式，Battery_Saving为低功耗模式，Device_Sensors是仅设备模式
        locationOption.locationMode = AMapLocationClientOption.AMapLocationMode.Hight_Accuracy;
        locationOption.isOnceLocation = true // 只需要定位一次
        locationClient.setLocationOption(locationOption);
        //启动定位
        locationClient.startLocation();
    }.promise


