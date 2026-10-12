package com.durdurnet
import android.content.Context
import android.view.View
import android.graphics.Color
class ViewDivider(context: Context) : View(context) {
    init { setBackgroundColor(Color.rgb(42, 52, 70)) }
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(1, MeasureSpec.EXACTLY))
    }
}
