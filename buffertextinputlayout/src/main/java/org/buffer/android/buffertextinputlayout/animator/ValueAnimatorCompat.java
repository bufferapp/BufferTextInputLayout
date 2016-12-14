/*
 * Copyright (C) 2009 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.buffer.android.buffertextinputlayout.animator;


import android.support.annotation.NonNull;
import android.view.animation.Interpolator;

/**
 * This class offers a very small subset of {@code ValueAnimator}'s API.
 * <p>
 * You shouldn't not instantiate this directly. Instead use {@code ViewUtils.createAnimator()}.
 */
public class ValueAnimatorCompat {

    public interface AnimatorUpdateListener {
        /**
         * <p>Notifies the occurrence of another frame of the animation.</p>
         *
         * @param animator The animation which was repeated.
         */
        void onAnimationUpdate(ValueAnimatorCompat animator);
    }

    public interface Creator {
        @NonNull
        ValueAnimatorCompat createAnimator();
    }

    static abstract class Impl {
        interface AnimatorUpdateListenerProxy {
            void onAnimationUpdate();
        }

        abstract void start();
        abstract boolean isRunning();
        abstract void setInterpolator(Interpolator interpolator);
        abstract void addUpdateListener(AnimatorUpdateListenerProxy updateListener);
        abstract void setFloatValues(float from, float to);
        abstract float getAnimatedFloatValue();
        abstract void setDuration(long duration);
        abstract void cancel();
        abstract void end();
        abstract long getDuration();
    }

    private final Impl impl;

    public ValueAnimatorCompat(Impl impl) {
        this.impl = impl;
    }

    public void start() {
        impl.start();
    }

    public boolean isRunning() {
        return impl.isRunning();
    }

    public void setInterpolator(Interpolator interpolator) {
        impl.setInterpolator(interpolator);
    }

    public void addUpdateListener(final AnimatorUpdateListener updateListener) {
        if (updateListener != null) {
            impl.addUpdateListener(new Impl.AnimatorUpdateListenerProxy() {
                @Override
                public void onAnimationUpdate() {
                    updateListener.onAnimationUpdate(ValueAnimatorCompat.this);
                }
            });
        } else {
            impl.addUpdateListener(null);
        }
    }

    public void setFloatValues(float from, float to) {
        impl.setFloatValues(from, to);
    }

    public float getAnimatedFloatValue() {
        return impl.getAnimatedFloatValue();
    }

    public void setDuration(long duration) {
        impl.setDuration(duration);
    }

    public void cancel() {
        impl.cancel();
    }

    public void end() {
        impl.end();
    }

    public long getDuration() {
        return impl.getDuration();
    }
}