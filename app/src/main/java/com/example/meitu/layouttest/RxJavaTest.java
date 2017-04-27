package com.example.meitu.layouttest;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import rx.Observable;
import rx.Scheduler;
import rx.Subscriber;
import rx.schedulers.Schedulers;

/*********************************************
 * Author: lhy 2017/2/25
 * ********************************************
 * Version: 版本
 * Author: lhy
 * Changes: 更新点
 * ********************************************
 */
public class RxJavaTest {
    public void onCreate() {
        Observable
                .create(new Observable.OnSubscribe<Bitmap>() {
                    @Override
                    public void call(Subscriber<? super Bitmap> subscriber) {
                        subscriber.onNext(BitmapFactory.decodeFile("sdcard/111.jpg"));
                        subscriber.onCompleted();
                    }
                })
                .observeOn(Schedulers.io())
                .subscribeOn(Schedulers.immediate())
                .subscribe(new Subscriber<Bitmap>() {
                    @Override
                    public void onCompleted() {

                    }

                    @Override
                    public void onError(Throwable e) {

                    }

                    @Override
                    public void onNext(Bitmap bitmap) {

                    }
                });
    }
}
