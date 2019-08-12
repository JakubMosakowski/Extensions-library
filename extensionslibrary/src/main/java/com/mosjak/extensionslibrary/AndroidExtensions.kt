package com.mosjak.extensionslibrary

import android.animation.Animator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.ActivityManager
import android.content.*
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.animation.Animation
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ScrollView
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

fun ViewGroup.inflate(
    layoutId: Int, attachToRoot: Boolean = false
): View = LayoutInflater.from(context).inflate(layoutId, this, attachToRoot)

fun View.expand(
    duration: Long = 300,
    listener: Animator.AnimatorListener? = null,
    destinationHeight: Int = -1
) {
    clearAnimation()
    visibility = View.VISIBLE

    val params = layoutParams as? ViewGroup.MarginLayoutParams?
    val margins = if (null != params) (params.leftMargin - params.rightMargin) else 0
    val width = (parent as ViewGroup).width - margins

    measure(
        View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    )

    val targetHeight = if (destinationHeight > -1) destinationHeight else this.measuredHeight
    layoutParams.height = 0

    val anim = ValueAnimator.ofInt(0, targetHeight)
    anim.addUpdateListener { valueAnimator ->
        val value = valueAnimator.animatedValue as Int
        val layoutParams = this@expand.layoutParams
        layoutParams.height = value
        this@expand.layoutParams = layoutParams
    }
    listener?.let { anim.addListener(it) }
    anim.interpolator = FastOutSlowInInterpolator()
    anim.duration = duration
    anim.start()
}

fun View.collapse(duration: Long = 300, listener: Animation.AnimationListener? = null) {
    clearAnimation()
    val interpolator = FastOutSlowInInterpolator()
    val initialHeight = measuredHeight
    val a = object : Animation() {
        override fun applyTransformation(interpolatedTime: Float, t: android.view.animation.Transformation?) {
            if (interpolatedTime == 1f) {
                visibility = View.GONE
            } else {
                layoutParams.height =
                    initialHeight - (initialHeight * interpolator.getInterpolation(interpolatedTime)).toInt()
                requestLayout()
            }
        }

        override fun willChangeBounds() = true
    }
    a.duration = duration
    listener?.let { a.setAnimationListener(it) }
    startAnimation(a)
}

fun View.isVisible(): Boolean = visibility == View.VISIBLE

fun View.isVisible(isVisible: Boolean) {
    visibility = if (isVisible) View.VISIBLE else View.GONE
}

fun isMarshmallow() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M

fun hideSoftwareKeyboard(activity: Activity) {
    activity.window.decorView.post {
        try {
            val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
        } catch (ignored: Exception) {
        }
    }
}

fun hideSoftwareKeyboard(context: Context, view: View?) {
    if (view == null) return

    val imm = context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.hideSoftInputFromWindow(view.windowToken, 0)
    (view as? EditText)?.post {
        try {
            view.clearFocus()
        } catch (ignored: Exception) {
        }
    }
}

fun showSoftwareKeyboard(context: Context, view: View) {
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
    imm.toggleSoftInputFromWindow(view.applicationWindowToken, InputMethodManager.SHOW_FORCED, 0)
}

fun getActivityFromContext(context: Context): Activity? {
    if (context is Activity) return context
    if (context is ContextWrapper) return getActivityFromContext(context.baseContext)
    return null
}

/**
 * Convert dp to px
 * @param dp Value in dp
 */
fun dp2px(dp: Int): Float = dp * Resources.getSystem().displayMetrics.density

fun px2dp(px: Int): Float = px / Resources.getSystem().displayMetrics.density

fun getResFloat(resources: Resources, resId: Int): Float {
    val outValue = TypedValue()
    resources.getValue(resId, outValue, true)
    return outValue.float
}

/**
 * @return [width, height] in pixels
 */
fun getScreenSize(context: Context): Array<Int> {
    val metrics = DisplayMetrics()
    val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    windowManager.defaultDisplay.getMetrics(metrics)

    return arrayOf(metrics.widthPixels, metrics.heightPixels)
}

fun Context.isPortrait(): Boolean {
    return resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
}

fun Activity.makeStatusBarLight() {
    window?.statusBarColor = ContextCompat.getColor(this, android.R.color.white)
    if (isMarshmallow()) {
        val flags = window.decorView.systemUiVisibility or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.decorView.systemUiVisibility = flags
    }
}

fun createImageFile(context: Context): File {
    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
    val imageFileName = "JPEG_" + timeStamp + "_"
    return File.createTempFile(imageFileName, ".jpg", context.filesDir)
}

fun saveBitmapAsTempFile(context: Context, bitmap: Bitmap): String {
    val tempFile = createImageFile(context)
    val fos = FileOutputStream(tempFile)
    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, fos)
    fos.flush()
    fos.close()
    return tempFile.absolutePath
}

fun getBitmapFromUri(context: Context, uri: Uri, maxDimension: Int = 480): Bitmap? {
    val sourceUri: Uri = if (null == uri.scheme) Uri.parse("file://$uri") else uri

    val parcelFileDescriptor = context.contentResolver.openFileDescriptor(sourceUri, "r")
    val fileDescriptor = parcelFileDescriptor?.fileDescriptor ?: return null
    var image: Bitmap? = null

    try {
        val bmo = BitmapFactory.Options()
        bmo.inJustDecodeBounds = true
        bmo.inPreferredConfig = Bitmap.Config.RGB_565

        BitmapFactory.decodeFileDescriptor(fileDescriptor, null, bmo)
        BitmapFactory.decodeStream(null)

        val sampleSize = calculateInSampleSize(bmo, maxDimension, maxDimension)
        bmo.inJustDecodeBounds = false
        bmo.inSampleSize = sampleSize

        image = BitmapFactory.decodeFileDescriptor(fileDescriptor, null, bmo)
    } catch (e: Exception) {
    } finally {
        parcelFileDescriptor.close()
    }

    return image
}

fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1
    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

fun checkCameraHardware(context: Context): Boolean =
    context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)

fun BottomNavigationView.clearSelection() {
    for (i in 0 until menu.size()) {
        menu.getItem(i).apply {
            isCheckable = false
            isChecked = false
            isCheckable = true
        }
    }
}

fun Context.getCurrentLanguage(): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        return this.resources.configuration.locales.get(0).language
    } else {
        @Suppress("DEPRECATION")
        return this.resources.configuration.locale.language
    }
}

fun Context.loadForegroundDrawable(): Drawable? {
    var foregroundDrawable: Drawable? = null
    val attrs = intArrayOf(R.attr.selectableItemBackground)
    val typedArray: TypedArray = this.obtainStyledAttributes(attrs)
    try {
        val backgroundResource = typedArray.getResourceId(0, 0)
        foregroundDrawable = ContextCompat.getDrawable(this, backgroundResource)
    } finally {
        typedArray.recycle()
        return foregroundDrawable
    }
}

fun Context.getAvailableMemory(): ActivityManager.MemoryInfo {
    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    return ActivityManager.MemoryInfo().also { memoryInfo ->
        activityManager.getMemoryInfo(memoryInfo)
    }
}

fun ScrollView.isScrollable(): Boolean {
    if (childCount < 1) return false
    val child = getChildAt(0) ?: return false

    return height < child.height + paddingTop + paddingBottom
}

fun View.isOverlapping(anotherView: View): Boolean {
    val firstPosition = IntArray(2)
    val secondPosition = IntArray(2)

    measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    getLocationOnScreen(firstPosition)

    anotherView.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
    anotherView.getLocationOnScreen(secondPosition)

    val w1 = measuredWidth
    val h1 = measuredHeight

    val w2 = anotherView.measuredWidth
    val h2 = anotherView.measuredHeight

    return firstPosition[0] < secondPosition[0] + w2
            && firstPosition[0] + w1 > secondPosition[0]
            && firstPosition[1] < secondPosition[1] + h2
            && firstPosition[1] + h1 > secondPosition[1]
}

fun Context.showPermissionSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.data = Uri.fromParts("package", packageName, null)

    startActivity(intent)
}

fun Context.showLocationSettings() {
    val intent = Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    startActivity(intent)
}

fun View.loadResources(itemBackground: Drawable?): Drawable? {
    var resultBackground = itemBackground

    if (null != resultBackground) return resultBackground
    val attrs = intArrayOf(R.attr.selectableItemBackground)
    val typedArray: TypedArray = context.obtainStyledAttributes(attrs)
    try {
        val backgroundResource = typedArray.getResourceId(0, 0)
        if (backgroundResource > 0) {
            resultBackground = ContextCompat.getDrawable(context, backgroundResource)
        }
    } catch (ignored: Exception) {

    } finally {
        typedArray.recycle()
    }

    return resultBackground
}


fun View.isOnScreen(): Boolean {
    if (!isShown) return false

    val viewPosition = Rect()
    val screenSize = getScreenSize(context)
    val screenRect = Rect(0, 0, screenSize[0], screenSize[0])
    getGlobalVisibleRect(viewPosition)

    return viewPosition.intersect(screenRect)
}

fun BottomNavigationView.markAsSelected(position: Int) {
    clearSelection()
    menu.getItem(position).isChecked = true
}

fun View.isInvisible(isInvisible: Boolean) {
    when (isInvisible) {
        true -> this.visibility = View.INVISIBLE
        false -> this.visibility = View.VISIBLE
    }
}

fun genRandomIntExcept(start: Int, end: Int, excluded: List<Int>): Int {
    val rand = Random()
    var random = start + rand.nextInt(end - start - excluded.size)
    for (ex in excluded) {
        if (random < ex) {
            break
        }
        random++
    }
    return random
}

fun getResizedImageHeight(aspectRatio: Float): Int {
    return (getScreenWidth() * aspectRatio).roundToInt()
}

fun getScreenWidth(): Int = Resources.getSystem().displayMetrics.widthPixels
