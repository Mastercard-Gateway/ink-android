# Ink [![Build Status](https://travis-ci.org/simplifycom/ink-android.svg?branch=master)](https://travis-ci.org/simplifycom/ink-android)

A light-weight, customizable view for capturing a signature or drawing in an Android app.

![screenshot](./screenshot.png)

## Import the Dependency [![maven-central](https://img.shields.io/maven-central/v/com.simplify/ink.svg)](http://search.maven.org/#search%7Cga%7C1%7Cg%3A%20%22com.simplify%22%2C%20a%3A%22ink%22)

To import the Android SDK, include it as a dependency in your build.gradle file
```groovy
compile 'com.simplify:ink:X.X.X'
```

## Usage

To use the library, you must include the InkView class in your project. A simple solution is to reference it directly into your layout:
```xml
<com.simplify.ink.InkView
    android:id="@+id/ink"
    android:layout_width="match_parent"
    android:layout_height="match_parent"/>
```

Then, within your code, fetch the view and initialize it:

```java
InkView ink = (InkView) findViewById(R.id.ink);
    ink.setColor(getResources().getColor(android.R.color.black));
    ink.setMinStrokeWidth(1.5f);
    ink.setMaxStrokeWidth(6f);
```

Features can be toggled on and off by using the custom attributes on the xml tag:

```xml
// add this to the root element in your layout
xmlns:app="http://schemas.android.com/apk/res-auto"

<com.simplify.ink.InkView
    android:id="@+id/ink"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    app:inkFlags="interpolation|responsiveWeight"/>
```

or by setting the flags manually in code:

```java
InkView ink = (InkView) findViewById(R.id.ink);
ink.setFlags(InkView.FLAG_INTERPOLATION | InkView.FLAG_RESPONSIVE_WEIGHT);
```

By default, interpolation and responsive weight flags are on.

You can capture the drawing in the form of a bitmap by calling:

```java
Bitmap drawing = ink.getBitmap();
```

or you can also include a background color:

```java
Bitmap drawing = ink.getBitmap(getResources().getColor(R.color.my_background_color));
```