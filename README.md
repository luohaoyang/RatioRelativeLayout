# RatioRelativeLayout

基于百分比的相对布局。与Support包中的PercentRelativeLayout不同之处：

- 子布局设置比例不使用百分比，通过设置父布局layout_widthSpec、layout_heightSpec来计算比例占比，如在480 x 800 px的机型上，RatioRelativeLayout为根布局，并且设置layout_widthSpec = 750，layout_heightSpec为1334（iOS 设计图）下，子布局中宽度有关的比例参数中（如：layout_ratioWidth），1即等于480/750 px，而子布局中高度相关的比例参数（如：layout_ratioHeight），1即等于800/1334 px。

- 父布局支持三种适配类型，：
  - fitXY：默认模式，真实的layout_widthSpec、layout_heightSpec与设置相同。
  - fitX：适应宽度，及真实的layout_widthSpec与设置相同，而layout_heightSpec通过真实的宽高比来计算，如在前面的例子中，layout_heightSpec = 750 / 480 * 800 = 1250。
  - fitY：适应高度，与fitX类似，相当于layout_widthSpec = 1334 / 800 * 480 = 800.4。

- 子布局宽高比aspectRatio支持320/480或者0.5两种格式。

- 支持子布局在宽高设置wrap_content或者match_parent的情况下设置子布局的宽高比aspectRatio。

- 如果RatioRelativeLayout之间有直接的嵌套关系，则内层的layout_widthSpec 与 layout_heightSpec在不指定的情况下会继承自计算后的layout_ratioWidth与layout_ratioHeight，但大多数情况下用不到多层嵌套。

  ​

## 布局属性

#### 自身属性

| 属性名               | 格式    | 说明    |
| ----------------- | ----- | ----- |
| layout_widthSpec  | float | 宽度总份数 |
| layout_heightSpec | float | 高度总份数 |
| adaptType         | enum  | 适配类型  |

#### 子布局属性

| 属性名                      | 格式     | 说明                           |
| ------------------------ | ------ | ---------------------------- |
| aspectRatio              | string | 宽高比                          |
| layout_ratioWidth        | float  | 比例宽度，与layout_widthSpec相关     |
| layout_ratioHeight       | float  | 比例高度，与layout_heightSpec相关    |
| layout_ratioMarginLeft   | float  | 比例margin，与layout_widthSpec相关 |
| layout_ratioMarginRight  | float  | 同上                           |
| layout_ratioMarginTop    | float  | 同上                           |
| layout_ratioMarginBottom | float  | 同上                           |



## 使用案例



```
<com.example.meitu.layouttest.RatioRelativeLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="#987"
    app:adaptType="fitX"
    app:layout_heightSpec="1334"
    app:layout_widthSpec="750">

    <ImageView
        android:id="@+id/iv_image"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_centerInParent="true"
        android:background="#fdf"
        android:src="@drawable/go_airbrush_popup_window_bg"
        app:layout_ratioHeight="860"
        app:layout_ratioWidth="590"/>

    <TextView
        android:id="@+id/tv_title"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_alignTop="@id/iv_image"
        android:layout_centerHorizontal="true"
        android:gravity="center"
        android:includeFontPadding="false"
        android:text="MMMMMMMMMMM"
        android:textColor="#ff813c"
        android:textSize="14dp"
        app:layout_ratioHeight="60"
        app:layout_ratioMarginTop="680"/>
    
    <Button
        android:id="@+id/iv_ok"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_alignBottom="@id/iv_image"
        android:layout_centerHorizontal="true"
        android:background="@drawable/go_airbrush_btn_bg_normal"
        android:gravity="center"
        android:text="Try it now"
        android:textColor="#fff"
        app:layout_ratioHeight="80"
        app:layout_ratioMarginBottom="30"
        app:layout_ratioWidth="360"/>

</com.example.meitu.layouttest.RatioRelativeLayout>
```