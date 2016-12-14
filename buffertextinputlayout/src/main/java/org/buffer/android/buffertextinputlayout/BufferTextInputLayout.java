/*
 * Copyright (C) 2015 The Android Open Source Project
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
package org.buffer.android.buffertextinputlayout;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.StyleRes;
import android.support.annotation.VisibleForTesting;
import android.support.design.widget.CheckableImageButton;
import android.support.design.widget.TextInputEditText;
import android.support.v4.content.ContextCompat;
import android.support.v4.graphics.drawable.DrawableCompat;
import android.support.v4.os.ParcelableCompat;
import android.support.v4.os.ParcelableCompatCreatorCallbacks;
import android.support.v4.view.AbsSavedState;
import android.support.v4.view.AccessibilityDelegateCompat;
import android.support.v4.view.GravityCompat;
import android.support.v4.view.ViewCompat;
import android.support.v4.view.ViewPropertyAnimatorListenerAdapter;
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat;
import android.support.v4.widget.Space;
import android.support.v4.widget.TextViewCompat;
import android.support.v7.content.res.AppCompatResources;
import android.support.v7.widget.AppCompatDrawableManager;
import android.support.v7.widget.TintTypedArray;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.animation.AccelerateInterpolator;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.buffer.android.buffertextinputlayout.util.AnimationUtils;
import org.buffer.android.buffertextinputlayout.util.DrawableUtils;
import org.buffer.android.buffertextinputlayout.util.ThemeUtils;
import org.buffer.android.buffertextinputlayout.util.ViewGroupUtils;
import org.buffer.android.buffertextinputlayout.util.ViewUtils;

/**
 * A simple customisation of the {@link android.support.design.widget.TextInputLayout} from the
 * design support library.
 *
 * The difference with the BufferTextInputLayout is that the counter can be displayed in three
 * different ways, being:
 *
 * DESCENDING - Starting from the set maximum counter value, the counter will decrement in value
 *              as the user types
 * ASCENDING - Starting from 0, the counter will increment in value as the user types
 * STANDARD - Displayed in the same way as the design support library (default). E.g 10/100
 *
 * As well as this, it is possible to set a value for charactersRemainingUntilCounterDisplay, this
 * value simply declares how many characters should be remaining until the counter becomes visible.
 * (Note, if this value is not set then the counter will always be visible).
 */
public class BufferTextInputLayout extends LinearLayout {

    private static final int ANIMATION_DURATION = 200;
    private static final int INVALID_MAX_LENGTH = -1;
    private static final String LOG_TAG = "CountDownText";
    private final FrameLayout inputFrame;
    EditText editText;
    private boolean isHintEnabled;
    private CharSequence hint;
    private Paint tempPaint;
    private final Rect tempRect = new Rect();
    private LinearLayout indicatorArea;
    private int indicatorsAdded;
    private boolean errorEnabled;
    TextView errorView;
    private int errorTextAppearance;
    private boolean errorShown;
    private CharSequence errorMessage;
    boolean counterEnabled;
    private TextView counterView;
    private int counterMaxLength;
    private int counterTextAppearance;
    private int counterOverflowTextAppearance;
    private boolean counterOverflowed;
    private boolean passwordToggleEnabled;
    private Drawable passwordToggleDrawable;
    private CharSequence passwordToggleContentDesc;
    private CheckableImageButton passwordToggleView;
    private boolean passwordToggledVisible;
    private Drawable passwordToggleDummyDrawable;
    private Drawable originalEditTextEndDrawable;
    private ColorStateList passwordToggleTintList;
    private boolean hasPasswordToggleTintList;
    private PorterDuff.Mode passwordToggleTintMode;
    private boolean hasPasswordToggleTintMode;
    private ColorStateList defaultTextColor;
    private ColorStateList focusedTextColor;
    // Only used for testing
    private boolean isHintExpanded;
    final CollapsingTextHelper collapsingTextHelper = new CollapsingTextHelper(this);
    private boolean hintAnimationEnabled;
    private ValueAnimatorCompat animator;
    private boolean hasReconstructedEditTextBackground;
    private boolean inDrawableStateChanged;
    private boolean counterVisible;

    private int charactersRemainingUntilCounterDisplay;
    private TextInputType textInputType;

    public BufferTextInputLayout(Context context) {
        this(context, null);
    }

    public BufferTextInputLayout(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public BufferTextInputLayout(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        ThemeUtils.checkAppCompatTheme(context);
        setOrientation(VERTICAL);
        setWillNotDraw(false);
        setAddStatesFromChildren(true);
        inputFrame = new FrameLayout(context);
        inputFrame.setAddStatesFromChildren(true);
        addView(inputFrame);
        collapsingTextHelper.setTextSizeInterpolator(AnimationUtils.FAST_OUT_SLOW_IN_INTERPOLATOR);
        collapsingTextHelper.setPositionInterpolator(new AccelerateInterpolator());
        collapsingTextHelper.setCollapsedTextGravity(Gravity.TOP | GravityCompat.START);
        isHintExpanded = collapsingTextHelper.getExpansionFraction() == 1f;
        final TintTypedArray a = TintTypedArray.obtainStyledAttributes(context, attrs,
                R.styleable.BufferTextInputLayout, defStyleAttr, R.style.BufferTextInputLayout);
        isHintEnabled = a.getBoolean(R.styleable.BufferTextInputLayout_hintEnabled, true);
        setHint(a.getText(R.styleable.BufferTextInputLayout_android_hint));
        hintAnimationEnabled = a.getBoolean(
                R.styleable.BufferTextInputLayout_hintAnimationEnabled, true);
        if (a.hasValue(R.styleable.BufferTextInputLayout_android_textColorHint)) {
            defaultTextColor = focusedTextColor =
                    a.getColorStateList(R.styleable.BufferTextInputLayout_android_textColorHint);
        }
        final int hintAppearance = a.getResourceId(
                R.styleable.BufferTextInputLayout_hintTextAppearance, -1);
        if (hintAppearance != -1) {
            setHintTextAppearance(
                    a.getResourceId(R.styleable.BufferTextInputLayout_hintTextAppearance, 0));
        }
        errorTextAppearance = a.getResourceId(
                R.styleable.BufferTextInputLayout_errorTextAppearance, 0);
        final boolean errorEnabled = a.getBoolean(R.styleable.BufferTextInputLayout_errorEnabled,
                false);
        final boolean counterEnabled = a.getBoolean(
                R.styleable.BufferTextInputLayout_counterEnabled, false);
        setCounterMaxLength(
                a.getInt(R.styleable.BufferTextInputLayout_counterMaxLength, INVALID_MAX_LENGTH));
        counterTextAppearance = a.getResourceId(
                R.styleable.BufferTextInputLayout_counterTextAppearance, 0);
        counterOverflowTextAppearance = a.getResourceId(
                R.styleable.BufferTextInputLayout_counterOverflowTextAppearance, 0);
        counterVisible = counterEnabled;

        int type = a.getInt(R.styleable.BufferTextInputLayout_textInputMode, 2);
        textInputType = TextInputType.fromId(type);

        charactersRemainingUntilCounterDisplay = a.getInt(
                R.styleable.BufferTextInputLayout_displayFromCount, getCounterMaxLength());

        a.recycle();
        setErrorEnabled(errorEnabled);
        setCounterEnabled(counterEnabled);
        setCounterVisible(counterVisible &&
                (charactersRemainingUntilCounterDisplay == getCounterMaxLength()));
        applyPasswordToggleTint();
        if (ViewCompat.getImportantForAccessibility(this)
                == ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_AUTO) {
            // Make sure we're important for accessibility if we haven't been explicitly not
            ViewCompat.setImportantForAccessibility(this,
                    ViewCompat.IMPORTANT_FOR_ACCESSIBILITY_YES);
        }
        ViewCompat.setAccessibilityDelegate(this, new TextInputAccessibilityDelegate());
    }

    @Override
    public void addView(View child, int index, final ViewGroup.LayoutParams params) {
        if (child instanceof EditText) {
            inputFrame.addView(child, new FrameLayout.LayoutParams(params));
            // Now use the EditText's LayoutParams as our own and update them to make enough space
            // for the label
            inputFrame.setLayoutParams(params);
            updateInputLayoutMargins();
            setEditText((EditText) child);
        } else {
            // Carry on adding the View...
            super.addView(child, index, params);
        }
    }

    /**
     * Set the count value that the counter labvel should be hidden until.
     */
    public void setCharactersRemainingUntilCounterDisplay(int remainingCharacters) {
        charactersRemainingUntilCounterDisplay = remainingCharacters;
        updateLabelState(true);
    }

    /**
     * Set the typeface to use for both the expanded and floating hint.
     *
     * @param typeface typeface to use, or {@code null} to use the default.
     */
    public void setTypeface(@Nullable Typeface typeface) {
        collapsingTextHelper.setTypefaces(typeface);
    }

    /**
     * Returns the typeface used for both the expanded and floating hint.
     */
    @NonNull
    public Typeface getTypeface() {
        // This could be either the collapsed or expanded
        return collapsingTextHelper.getCollapsedTypeface();
    }

    private void setEditText(EditText editText) {
        // If we already have an EditText, throw an exception
        if (this.editText != null) {
            throw new IllegalArgumentException("We already have an EditText, can only have one");
        }
        if (!(editText instanceof TextInputEditText)) {
            Log.i(LOG_TAG, "EditText added is not a TextInputEditText. Please switch to using that"
                    + " class instead.");
        }
        this.editText = editText;
        final boolean hasPasswordTransformation = hasPasswordTransformation();
        // Use the EditText's typeface, and it's text size for our expanded text
        if (!hasPasswordTransformation) {
            // We don't want a monospace font just because we have a password field
            collapsingTextHelper.setTypefaces(this.editText.getTypeface());
        }
        collapsingTextHelper.setExpandedTextSize(this.editText.getTextSize());
        final int editTextGravity = this.editText.getGravity();
        collapsingTextHelper.setCollapsedTextGravity(
                Gravity.TOP | (editTextGravity & GravityCompat.RELATIVE_HORIZONTAL_GRAVITY_MASK));
        collapsingTextHelper.setExpandedTextGravity(editTextGravity);
        // Add a TextWatcher so that we know when the text input has changed
        this.editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void afterTextChanged(Editable s) {
                setCounterVisible(counterVisible &&
                        s.length() >= (getCounterMaxLength() - charactersRemainingUntilCounterDisplay));
                updateLabelState(true);
                if (counterEnabled) {
                    updateCounter(s.length());
                }
            }

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }
        });
        // Use the EditText's hint colors if we don't have one set
        if (defaultTextColor == null) {
            defaultTextColor = this.editText.getHintTextColors();
        }
        // If we do not have a valid hint, try and retrieve it from the EditText, if enabled
        if (isHintEnabled && TextUtils.isEmpty(hint)) {
            setHint(this.editText.getHint());
            // Clear the EditText's hint as we will display it ourselves
            this.editText.setHint(null);
        }
        if (counterView != null) {
            updateCounter(this.editText.getText().length());
        }
        if (indicatorArea != null) {
            adjustIndicatorPadding();
        }
        updatePasswordToggleView();
        // Update the label visibility with no animation
        updateLabelState(false);
    }

    private void updateInputLayoutMargins() {
        // Create/update the LayoutParams so that we can add enough top margin
        // to the EditText so make room for the label
        final LayoutParams lp = (LayoutParams) inputFrame.getLayoutParams();
        final int newTopMargin;
        if (isHintEnabled) {
            if (tempPaint == null) {
                tempPaint = new Paint();
            }
            tempPaint.setTypeface(collapsingTextHelper.getCollapsedTypeface());
            tempPaint.setTextSize(collapsingTextHelper.getCollapsedTextSize());
            newTopMargin = (int) -tempPaint.ascent();
        } else {
            newTopMargin = 0;
        }
        if (newTopMargin != lp.topMargin) {
            lp.topMargin = newTopMargin;
            inputFrame.requestLayout();
        }
    }

    void updateLabelState(boolean animate) {
        final boolean isEnabled = isEnabled();
        final boolean hasText = editText != null && !TextUtils.isEmpty(editText.getText());
        final boolean isFocused = arrayContains(getDrawableState(), android.R.attr.state_focused);
        final boolean isErrorShowing = !TextUtils.isEmpty(getError());
        if (defaultTextColor != null) {
            collapsingTextHelper.setExpandedTextColor(defaultTextColor);
        }
        if (isEnabled && counterOverflowed && counterView != null) {
            collapsingTextHelper.setCollapsedTextColor(counterView.getTextColors());
        } else if (isEnabled && isFocused && focusedTextColor != null) {
            collapsingTextHelper.setCollapsedTextColor(focusedTextColor);
        } else if (defaultTextColor != null) {
            collapsingTextHelper.setCollapsedTextColor(defaultTextColor);
        }
        if (hasText || (isEnabled() && (isFocused || isErrorShowing))) {
            // We should be showing the label so do so if it isn't already
            collapseHint(animate);
        } else {
            // We should not be showing the label so hide it
            expandHint(animate);
        }
    }

    /**
     * Returns the {@link android.widget.EditText} used for text input.
     */
    @Nullable
    public EditText getEditText() {
        return editText;
    }

    /**
     * Set the hint to be displayed in the floating label, if enabled.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_android_hint
     * @see #setHintEnabled(boolean)
     */
    public void setHint(@Nullable CharSequence hint) {
        if (isHintEnabled) {
            setHintInternal(hint);
            sendAccessibilityEvent(AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED);
        }
    }

    private void setHintInternal(CharSequence hint) {
        this.hint = hint;
        collapsingTextHelper.setText(hint);
    }

    /**
     * Returns the hint which is displayed in the floating label, if enabled.
     *
     * @return the hint, or null if there isn't one set, or the hint is not enabled.
     * @attr ref android.support.design.R.styleable#TextInputLayout_android_hint
     */
    @Nullable
    public CharSequence getHint() {
        return isHintEnabled ? hint : null;
    }

    /**
     * Sets whether the floating label functionality is enabled or not in this layout.
     * <p>
     * <p>If enabled, any non-empty hint in the child EditText will be moved into the floating
     * hint, and its existing hint will be cleared. If disabled, then any non-empty floating hint
     * in this layout will be moved into the EditText, and this layout's hint will be cleared.</p>
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_hintEnabled
     * @see #setHint(CharSequence)
     * @see #isHintEnabled()
     */
    public void setHintEnabled(boolean enabled) {
        if (enabled != isHintEnabled) {
            isHintEnabled = enabled;
            final CharSequence editTextHint = editText.getHint();
            if (!isHintEnabled) {
                if (!TextUtils.isEmpty(hint) && TextUtils.isEmpty(editTextHint)) {
                    // If the hint is disabled, but we have a hint set, and the EditText doesn't,
                    // pass it through...
                    editText.setHint(hint);
                }
                // Now clear out any set hint
                setHintInternal(null);
            } else {
                if (!TextUtils.isEmpty(editTextHint)) {
                    // If the hint is now enabled and the EditText has one set, we'll use it if
                    // we don't already have one, and clear the EditText's
                    if (TextUtils.isEmpty(hint)) {
                        setHint(editTextHint);
                    }
                    editText.setHint(null);
                }
            }
            // Now update the EditText top margin
            if (editText != null) {
                updateInputLayoutMargins();
            }
        }
    }

    /**
     * Returns whether the floating label functionality is enabled or not in this layout.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_hintEnabled
     * @see #setHintEnabled(boolean)
     */
    public boolean isHintEnabled() {
        return isHintEnabled;
    }

    /**
     * Sets the hint text color, size, style from the specified TextAppearance resource.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_hintTextAppearance
     */
    public void setHintTextAppearance(@StyleRes int resId) {
        collapsingTextHelper.setCollapsedTextAppearance(resId);
        focusedTextColor = collapsingTextHelper.getCollapsedTextColor();
        if (editText != null) {
            updateLabelState(false);
            // Text size might have changed so update the top margin
            updateInputLayoutMargins();
        }
    }

    private void addIndicator(TextView indicator, int index) {
        if (indicatorArea == null) {
            indicatorArea = new LinearLayout(getContext());
            indicatorArea.setOrientation(LinearLayout.HORIZONTAL);
            addView(indicatorArea, LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            // Add a flexible spacer in the middle so that the left/right views stay pinned
            final Space spacer = new Space(getContext());
            final LinearLayout.LayoutParams spacerLp = new LinearLayout.LayoutParams(0, 0, 1f);
            indicatorArea.addView(spacer, spacerLp);
            if (editText != null) {
                adjustIndicatorPadding();
            }
        }
        indicatorArea.setVisibility(View.VISIBLE);
        indicatorArea.addView(indicator, index);
        indicatorsAdded++;
    }

    private void adjustIndicatorPadding() {
        // Add padding to the error and character counter so that they match the EditText
        ViewCompat.setPaddingRelative(indicatorArea, ViewCompat.getPaddingStart(editText),
                0, ViewCompat.getPaddingEnd(editText), editText.getPaddingBottom());
    }

    private void removeIndicator(TextView indicator) {
        if (indicatorArea != null) {
            indicatorArea.removeView(indicator);
            if (--indicatorsAdded == 0) {
                indicatorArea.setVisibility(View.GONE);
            }
        }
    }

    /**
     * Whether the error functionality is enabled or not in this layout. Enabling this
     * functionality before setting an error message via {@link #setError(CharSequence)}, will mean
     * that this layout will not change size when an error is displayed.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_errorEnabled
     */
    public void setErrorEnabled(boolean enabled) {
        if (errorEnabled != enabled) {
            if (errorView != null) {
                ViewCompat.animate(errorView).cancel();
            }
            if (enabled) {
                errorView = new TextView(getContext());
                boolean useDefaultColor = false;
                try {
                    TextViewCompat.setTextAppearance(errorView, errorTextAppearance);
                    if (Build.VERSION.SDK_INT >= 23
                            && errorView.getTextColors().getDefaultColor() == Color.MAGENTA) {
                        // Caused by our theme not extending from Theme.Design*. On API 23 and
                        // above, unresolved theme attrs result in MAGENTA rather than an exception.
                        // Flag so that we use a decent default
                        useDefaultColor = true;
                    }
                } catch (Exception e) {
                    // Caused by our theme not extending from Theme.Design*. Flag so that we use
                    // a decent default
                    useDefaultColor = true;
                }
                if (useDefaultColor) {
                    // Probably caused by our theme not extending from Theme.Design*. Instead
                    // we manually set something appropriate
                    TextViewCompat.setTextAppearance(errorView,
                            android.support.v7.appcompat.R.style.TextAppearance_AppCompat_Caption);
                    errorView.setTextColor(ContextCompat.getColor(
                            getContext(), R.color.design_textinput_error_color_light));
                }
                errorView.setVisibility(INVISIBLE);
                ViewCompat.setAccessibilityLiveRegion(errorView,
                        ViewCompat.ACCESSIBILITY_LIVE_REGION_POLITE);
                addIndicator(errorView, 0);
            } else {
                errorShown = false;
                updateEditTextBackground();
                removeIndicator(errorView);
                errorView = null;
            }
            errorEnabled = enabled;
        }
    }

    /**
     * Returns whether the error functionality is enabled or not in this layout.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_errorEnabled
     * @see #setErrorEnabled(boolean)
     */
    public boolean isErrorEnabled() {
        return errorEnabled;
    }

    /**
     * Sets an error message that will be displayed below our {@link EditText}. If the
     * {@code error} is {@code null}, the error message will be cleared.
     * <p>
     * If the error functionality has not been enabled via {@link #setErrorEnabled(boolean)}, then
     * it will be automatically enabled if {@code error} is not empty.
     *
     * @param error Error message to display, or null to clear
     * @see #getError()
     */
    public void setError(@Nullable final CharSequence error) {
        // Only animate if we're enabled, laid out, and we have a different error message
        setError(error, ViewCompat.isLaidOut(this) && isEnabled()
                && (errorView == null || !TextUtils.equals(errorView.getText(), error)));
    }

    private void setError(@Nullable final CharSequence error, final boolean animate) {
        errorMessage = error;
        if (!errorEnabled) {
            if (TextUtils.isEmpty(error)) {
                // If error isn't enabled, and the error is empty, just return
                return;
            }
            // Else, we'll assume that they want to enable the error functionality
            setErrorEnabled(true);
        }
        errorShown = !TextUtils.isEmpty(error);
        // Cancel any on-going animation
        ViewCompat.animate(errorView).cancel();
        if (errorShown) {
            errorView.setText(error);
            errorView.setVisibility(VISIBLE);
            if (animate) {
                if (ViewCompat.getAlpha(errorView) == 1f) {
                    // If it's currently 100% show, we'll animate it from 0
                    ViewCompat.setAlpha(errorView, 0f);
                }
                ViewCompat.animate(errorView)
                        .alpha(1f)
                        .setDuration(ANIMATION_DURATION)
                        .setInterpolator(AnimationUtils.LINEAR_OUT_SLOW_IN_INTERPOLATOR)
                        .setListener(new ViewPropertyAnimatorListenerAdapter() {
                            @Override
                            public void onAnimationStart(View view) {
                                view.setVisibility(VISIBLE);
                            }
                        }).start();
            } else {
                // Set alpha to 1f, just in case
                ViewCompat.setAlpha(errorView, 1f);
            }
        } else {
            if (errorView.getVisibility() == VISIBLE) {
                if (animate) {
                    ViewCompat.animate(errorView)
                            .alpha(0f)
                            .setDuration(ANIMATION_DURATION)
                            .setInterpolator(AnimationUtils.FAST_OUT_LINEAR_IN_INTERPOLATOR)
                            .setListener(new ViewPropertyAnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(View view) {
                                    errorView.setText(error);
                                    view.setVisibility(INVISIBLE);
                                }
                            }).start();
                } else {
                    errorView.setText(error);
                    errorView.setVisibility(INVISIBLE);
                }
            }
        }
        updateEditTextBackground();
        updateLabelState(animate);
    }

    /**
     * Whether the character counter functionality is enabled or not in this layout.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_counterEnabled
     */
    public void setCounterEnabled(boolean enabled) {
        if (counterEnabled != enabled) {
            if (enabled) {
                counterView = new TextView(getContext());
                counterView.setMaxLines(1);
                try {
                    TextViewCompat.setTextAppearance(counterView, counterTextAppearance);
                } catch (Exception e) {
                    // Probably caused by our theme not extending from Theme.Design*. Instead
                    // we manually set something appropriate
                    TextViewCompat.setTextAppearance(counterView,
                            android.support.v7.appcompat.R.style.TextAppearance_AppCompat_Caption);
                    counterView.setTextColor(ContextCompat.getColor(
                            getContext(), R.color.design_textinput_error_color_light));
                }
                addIndicator(counterView, -1);
                if (editText == null) {
                    updateCounter(0);
                } else {
                    updateCounter(editText.getText().length());
                }
            } else {
                removeIndicator(counterView);
                counterView = null;
            }
            counterEnabled = enabled;
        }
    }

    public void setCounterVisible(boolean visible) {
        if (counterView != null) {
            counterView.setVisibility(visible ? VISIBLE : GONE);
        }
    }

    /**
     * Returns whether the character counter functionality is enabled or not in this layout.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_counterEnabled
     * @see #setCounterEnabled(boolean)
     */
    public boolean isCounterEnabled() {
        return counterEnabled;
    }

    /**
     * Sets the max length to display at the character counter.
     *
     * @param maxLength maxLength to display. Any value less than or equal to 0 will not be shown.
     * @attr ref android.support.design.R.styleable#TextInputLayout_counterMaxLength
     */
    public void setCounterMaxLength(int maxLength) {
        if (counterMaxLength != maxLength) {
            if (maxLength > 0) {
                counterMaxLength = maxLength;
            } else {
                counterMaxLength = INVALID_MAX_LENGTH;
            }
            if (counterEnabled) {
                updateCounter(editText == null ? 0 : editText.getText().length());
            }
        }
    }

    @Override
    public void setEnabled(boolean enabled) {
        // Since we're set to addStatesFromChildren, we need to make sure that we set all
        // children to enabled/disabled otherwise any enabled children will wipe out our disabled
        // drawable state
        recursiveSetEnabled(this, enabled);
        super.setEnabled(enabled);
    }

    private static void recursiveSetEnabled(final ViewGroup vg, final boolean enabled) {
        for (int i = 0, count = vg.getChildCount(); i < count; i++) {
            final View child = vg.getChildAt(i);
            child.setEnabled(enabled);
            if (child instanceof ViewGroup) {
                recursiveSetEnabled((ViewGroup) child, enabled);
            }
        }
    }

    /**
     * Returns the max length shown at the character counter.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_counterMaxLength
     */
    public int getCounterMaxLength() {
        return counterMaxLength;
    }

    void updateCounter(int length) {
        boolean wasCounterOverflowed = counterOverflowed;
        if (counterMaxLength == INVALID_MAX_LENGTH) {
            counterView.setText(String.valueOf(length));
            counterOverflowed = false;
        } else {
            counterOverflowed = length > counterMaxLength;
            if (wasCounterOverflowed != counterOverflowed) {
                TextViewCompat.setTextAppearance(counterView, counterOverflowed ?
                        counterOverflowTextAppearance : counterTextAppearance);
            }
            setCounterText(length);
        }
        if (editText != null && wasCounterOverflowed != counterOverflowed) {
            updateLabelState(false);
            updateEditTextBackground();
        }
    }

    void setCounterText(int length) {
        String text;
        switch (textInputType) {
            case DESCENDING:
                text = String.valueOf(counterMaxLength - length);
                break;
            case ASCENDING:
                text = String.valueOf(length);
                break;
            default:
                text = getContext().getString(R.string.standard_character_counter_pattern, length,
                        counterMaxLength);
                break;
        }
        counterView.setText(text);
    }

    private void updateEditTextBackground() {
        if (editText == null) {
            return;
        }
        Drawable editTextBackground = editText.getBackground();
        if (editTextBackground == null) {
            return;
        }
        ensureBackgroundDrawableStateWorkaround();
        if (android.support.v7.widget.DrawableUtils.canSafelyMutateDrawable(editTextBackground)) {
            editTextBackground = editTextBackground.mutate();
        }
        if (errorShown && errorView != null) {
            // Set a color filter of the error color
            editTextBackground.setColorFilter(
                    AppCompatDrawableManager.getPorterDuffColorFilter(
                            errorView.getCurrentTextColor(), PorterDuff.Mode.SRC_IN));
        } else if (counterOverflowed && counterView != null) {
            // Set a color filter of the counter color
            editTextBackground.setColorFilter(
                    AppCompatDrawableManager.getPorterDuffColorFilter(
                            counterView.getCurrentTextColor(), PorterDuff.Mode.SRC_IN));
        } else {
            // Else reset the color filter and refresh the drawable state so that the
            // normal tint is used
            DrawableCompat.clearColorFilter(editTextBackground);
            editText.refreshDrawableState();
        }
    }

    private void ensureBackgroundDrawableStateWorkaround() {
        final int sdk = Build.VERSION.SDK_INT;
        if (sdk != 21 && sdk != 22) {
            // The workaround is only required on API 21-22
            return;
        }
        final Drawable bg = editText.getBackground();
        if (bg == null) {
            return;
        }
        if (!hasReconstructedEditTextBackground) {
            // This is gross. There is an issue in the platform which affects container Drawables
            // where the first drawable retrieved from resources will propagate any changes
            // (like color filter) to all instances from the cache. We'll try to workaround it...
            final Drawable newBg = bg.getConstantState().newDrawable();
            if (bg instanceof DrawableContainer) {
                // If we have a Drawable container, we can try and set it's constant state via
                // reflection from the new Drawable
                hasReconstructedEditTextBackground =
                        DrawableUtils.setContainerConstantState(
                                (DrawableContainer) bg, newBg.getConstantState());
            }
            if (!hasReconstructedEditTextBackground) {
                // If we reach here then we just need to set a brand new instance of the Drawable
                // as the background. This has the unfortunate side-effect of wiping out any
                // user set padding, but I'd hope that use of custom padding on an EditText
                // is limited.
                ViewCompat.setBackground(editText, newBg);
                hasReconstructedEditTextBackground = true;
            }
        }
    }

    static class SavedState extends AbsSavedState {
        CharSequence error;

        SavedState(Parcelable superState) {
            super(superState);
        }

        public SavedState(Parcel source, ClassLoader loader) {
            super(source, loader);
            error = TextUtils.CHAR_SEQUENCE_CREATOR.createFromParcel(source);
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            super.writeToParcel(dest, flags);
            TextUtils.writeToParcel(error, dest, flags);
        }

        @Override
        public String toString() {
            return "TextInputLayout.SavedState{"
                    + Integer.toHexString(System.identityHashCode(this))
                    + " error=" + error + "}";
        }

        public static final Creator<SavedState> CREATOR = ParcelableCompat.newCreator(
                new ParcelableCompatCreatorCallbacks<SavedState>() {
                    @Override
                    public SavedState createFromParcel(Parcel in, ClassLoader loader) {
                        return new SavedState(in, loader);
                    }

                    @Override
                    public SavedState[] newArray(int size) {
                        return new SavedState[size];
                    }
                });
    }

    @Override
    public Parcelable onSaveInstanceState() {
        Parcelable superState = super.onSaveInstanceState();
        SavedState ss = new SavedState(superState);
        if (errorShown) {
            ss.error = getError();
        }
        return ss;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        if (!(state instanceof SavedState)) {
            super.onRestoreInstanceState(state);
            return;
        }
        SavedState ss = (SavedState) state;
        super.onRestoreInstanceState(ss.getSuperState());
        setError(ss.error);
        requestLayout();
    }

    /**
     * Returns the error message that was set to be displayed with
     * {@link #setError(CharSequence)}, or <code>null</code> if no error was set
     * or if error displaying is not enabled.
     *
     * @see #setError(CharSequence)
     */
    @Nullable
    public CharSequence getError() {
        return errorEnabled ? errorMessage : null;
    }

    /**
     * Returns whether any hint state changes, due to being focused or non-empty text, are
     * animated.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_hintAnimationEnabled
     * @see #setHintAnimationEnabled(boolean)
     */
    public boolean isHintAnimationEnabled() {
        return hintAnimationEnabled;
    }

    /**
     * Set whether any hint state changes, due to being focused or non-empty text, are
     * animated.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_hintAnimationEnabled
     * @see #isHintAnimationEnabled()
     */
    public void setHintAnimationEnabled(boolean enabled) {
        hintAnimationEnabled = enabled;
    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        if (isHintEnabled) {
            collapsingTextHelper.draw(canvas);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        updatePasswordToggleView();
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    private void updatePasswordToggleView() {
        if (editText == null) {
            // If there is no EditText, there is nothing to update
            return;
        }
        if (shouldShowPasswordIcon()) {
            if (passwordToggleView == null) {
                passwordToggleView = (CheckableImageButton) LayoutInflater.from(getContext())
                        .inflate(R.layout.design_text_input_password_icon, inputFrame, false);
                passwordToggleView.setImageDrawable(passwordToggleDrawable);
                passwordToggleView.setContentDescription(passwordToggleContentDesc);
                inputFrame.addView(passwordToggleView);
                passwordToggleView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        passwordVisibilityToggleRequested();
                    }
                });
            }
            passwordToggleView.setVisibility(VISIBLE);
            // We need to add a dummy drawable as the end compound drawable so that the text is
            // indented and doesn't display below the toggle view
            if (passwordToggleDummyDrawable == null) {
                passwordToggleDummyDrawable = new ColorDrawable();
            }
            passwordToggleDummyDrawable.setBounds(0, 0, passwordToggleView.getMeasuredWidth(), 1);
            final Drawable[] compounds = TextViewCompat.getCompoundDrawablesRelative(editText);
            // Store the user defined end compound drawable so that we can restore it later
            if (compounds[2] != passwordToggleDummyDrawable) {
                originalEditTextEndDrawable = compounds[2];
            }
            TextViewCompat.setCompoundDrawablesRelative(editText, compounds[0], compounds[1],
                    passwordToggleDummyDrawable, compounds[3]);
            // Copy over the EditText's padding so that we match
            passwordToggleView.setPadding(editText.getPaddingLeft(),
                    editText.getPaddingTop(), editText.getPaddingRight(),
                    editText.getPaddingBottom());
        } else {
            if (passwordToggleView != null && passwordToggleView.getVisibility() == VISIBLE) {
                passwordToggleView.setVisibility(View.GONE);
            }
            // Make sure that we remove the dummy end compound drawable
            final Drawable[] compounds = TextViewCompat.getCompoundDrawablesRelative(editText);
            if (compounds[2] == passwordToggleDummyDrawable) {
                TextViewCompat.setCompoundDrawablesRelative(editText, compounds[0], compounds[1],
                        originalEditTextEndDrawable, compounds[3]);
            }
        }
    }

    /**
     * Set the icon to use for the password visibility toggle button.
     * <p>
     * <p>If you use an icon you should also set a description for its action
     * using {@link #setPasswordVisibilityToggleContentDescription(CharSequence)}.
     * This is used for accessibility.</p>
     *
     * @param resId resource id of the drawable to set, or 0 to clear the icon
     * @attr ref android.support.design.R.styleable#TextInputLayout_passwordToggleDrawable
     */
    public void setPasswordVisibilityToggleDrawable(@DrawableRes int resId) {
        setPasswordVisibilityToggleDrawable(resId != 0
                ? AppCompatResources.getDrawable(getContext(), resId)
                : null);
    }

    /**
     * Set the icon to use for the password visibility toggle button.
     * <p>
     * <p>If you use an icon you should also set a description for its action
     * using {@link #setPasswordVisibilityToggleContentDescription(CharSequence)}.
     * This is used for accessibility.</p>
     *
     * @param icon Drawable to set, may be null to clear the icon
     * @attr ref android.support.design.R.styleable#TextInputLayout_passwordToggleDrawable
     */
    public void setPasswordVisibilityToggleDrawable(@Nullable Drawable icon) {
        passwordToggleDrawable = icon;
        if (passwordToggleView != null) {
            passwordToggleView.setImageDrawable(icon);
        }
    }

    /**
     * Set a content description for the navigation button if one is present.
     * <p>
     * <p>The content description will be read via screen readers or other accessibility
     * systems to explain the action of the password visibility toggle.</p>
     *
     * @param resId Resource ID of a content description string to set,
     *              or 0 to clear the description
     * @attr ref android.support.design.R.styleable#TextInputLayout_passwordToggleContentDescription
     */
    public void setPasswordVisibilityToggleContentDescription(@StringRes int resId) {
        setPasswordVisibilityToggleContentDescription(
                resId != 0 ? getResources().getText(resId) : null);
    }

    /**
     * Set a content description for the navigation button if one is present.
     * <p>
     * <p>The content description will be read via screen readers or other accessibility
     * systems to explain the action of the password visibility toggle.</p>
     *
     * @param description Content description to set, or null to clear the content description
     * @attr ref android.support.design.R.styleable#TextInputLayout_passwordToggleContentDescription
     */
    public void setPasswordVisibilityToggleContentDescription(@Nullable CharSequence description) {
        passwordToggleContentDesc = description;
        if (passwordToggleView != null) {
            passwordToggleView.setContentDescription(description);
        }
    }

    /**
     * Returns the icon currently used for the password visibility toggle button.
     *
     * @attr ref android.support.design.R.styleable#TextInputLayout_passwordToggleDrawable
     * @see #setPasswordVisibilityToggleDrawable(Drawable)
     */
    @Nullable
    public Drawable getPasswordVisibilityToggleDrawable() {
        return passwordToggleDrawable;
    }

    /**
     * Returns the currently configured content description for the password visibility
     * toggle button.
     * <p>
     * <p>This will be used to describe the navigation action to users through mechanisms
     * such as screen readers.</p>
     */
    @Nullable
    public CharSequence getPasswordVisibilityToggleContentDescription() {
        return passwordToggleContentDesc;
    }

    /**
     * Returns whether the password visibility toggle functionality is currently enabled.
     *
     * @see #setPasswordVisibilityToggleEnabled(boolean)
     */
    public boolean isPasswordVisibilityToggleEnabled() {
        return passwordToggleEnabled;
    }

    /**
     * Returns whether the password visibility toggle functionality is enabled or not.
     * <p>
     * <p>When enabled, a button is placed at the end of the EditText which enables the user
     * to switch between the field's input being visibly disguised or not.</p>
     *
     * @param enabled true to enable the functionality
     * @attr ref android.support.design.R.styleable#TextInputLayout_passwordToggleEnabled
     */
    public void setPasswordVisibilityToggleEnabled(final boolean enabled) {
        if (passwordToggleEnabled != enabled) {
            passwordToggleEnabled = enabled;
            if (!enabled && passwordToggledVisible && editText != null) {
                // If the toggle is no longer enabled, but we remove the PasswordTransformation
                // to make the password visible, add it back
                editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
            }
            // Reset the visibility tracking flag
            passwordToggledVisible = false;
            updatePasswordToggleView();
        }
    }

    /**
     * Applies a tint to the the password visibility toggle drawable. Does not modify the current
     * tint mode, which is {@link PorterDuff.Mode#SRC_IN} by default.
     * <p>
     * <p>Subsequent calls to {@link #setPasswordVisibilityToggleDrawable(Drawable)} will
     * automatically mutate the drawable and apply the specified tint and tint mode using
     * {@link DrawableCompat#setTintList(Drawable, ColorStateList)}.</p>
     *
     * @param tintList the tint to apply, may be null to clear tint
     * @attr ref android.support.design.R.styleable#TextInputLayout_passwordToggleTint
     */
    public void setPasswordVisibilityToggleTintList(@Nullable ColorStateList tintList) {
        passwordToggleTintList = tintList;
        hasPasswordToggleTintList = true;
        applyPasswordToggleTint();
    }

    /**
     * Specifies the blending mode used to apply the tint specified by
     * {@link #setPasswordVisibilityToggleTintList(ColorStateList)} to the password
     * visibility toggle drawable. The default mode is {@link PorterDuff.Mode#SRC_IN}.</p>
     *
     * @param mode the blending mode used to apply the tint, may be null to clear tint
     * @attr ref android.support.design.R.styleable#TextInputLayout_passwordToggleTintMode
     */
    public void setPasswordVisibilityToggleTintMode(@Nullable PorterDuff.Mode mode) {
        passwordToggleTintMode = mode;
        hasPasswordToggleTintMode = true;
        applyPasswordToggleTint();
    }

    void passwordVisibilityToggleRequested() {
        if (passwordToggleEnabled) {
            // Store the current cursor position
            final int selection = editText.getSelectionEnd();
            if (hasPasswordTransformation()) {
                editText.setTransformationMethod(null);
                passwordToggledVisible = true;
            } else {
                editText.setTransformationMethod(PasswordTransformationMethod.getInstance());
                passwordToggledVisible = false;
            }
            passwordToggleView.setChecked(passwordToggledVisible);
            // And restore the cursor position
            editText.setSelection(selection);
        }
    }

    private boolean hasPasswordTransformation() {
        return editText != null
                && editText.getTransformationMethod() instanceof PasswordTransformationMethod;
    }

    private boolean shouldShowPasswordIcon() {
        return passwordToggleEnabled && (hasPasswordTransformation() || passwordToggledVisible);
    }

    private void applyPasswordToggleTint() {
        if (passwordToggleDrawable != null
                && (hasPasswordToggleTintList || hasPasswordToggleTintMode)) {
            passwordToggleDrawable = DrawableCompat.wrap(passwordToggleDrawable).mutate();
            if (hasPasswordToggleTintList) {
                DrawableCompat.setTintList(passwordToggleDrawable, passwordToggleTintList);
            }
            if (hasPasswordToggleTintMode) {
                DrawableCompat.setTintMode(passwordToggleDrawable, passwordToggleTintMode);
            }
            if (passwordToggleView != null
                    && passwordToggleView.getDrawable() != passwordToggleDrawable) {
                passwordToggleView.setImageDrawable(passwordToggleDrawable);
            }
        }
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (isHintEnabled && editText != null) {
            final Rect rect = tempRect;
            ViewGroupUtils.getDescendantRect(this, editText, rect);
            final int l = rect.left + editText.getCompoundPaddingLeft();
            final int r = rect.right - editText.getCompoundPaddingRight();
            collapsingTextHelper.setExpandedBounds(
                    l, rect.top + editText.getCompoundPaddingTop(),
                    r, rect.bottom - editText.getCompoundPaddingBottom());
            // Set the collapsed bounds to be the the full height (minus padding) to match the
            // EditText's editable area
            collapsingTextHelper.setCollapsedBounds(l, getPaddingTop(),
                    r, bottom - top - getPaddingBottom());
            collapsingTextHelper.recalculate();
        }
    }

    private void collapseHint(boolean animate) {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        if (animate && hintAnimationEnabled) {
            animateToExpansionFraction(1f);
        } else {
            collapsingTextHelper.setExpansionFraction(1f);
        }
        isHintExpanded = false;
    }

    @Override
    protected void drawableStateChanged() {
        if (inDrawableStateChanged) {
            // Some of the calls below will update the drawable state of child views. Since we're
            // using addStatesFromChildren we can get into infinite recursion, hence we'll just
            // exit in this instance
            return;
        }
        inDrawableStateChanged = true;
        super.drawableStateChanged();
        final int[] state = getDrawableState();
        boolean changed = false;
        // Drawable state has changed so see if we need to update the label
        updateLabelState(ViewCompat.isLaidOut(this) && isEnabled());
        updateEditTextBackground();
        if (collapsingTextHelper != null) {
            changed |= collapsingTextHelper.setState(state);
        }
        if (changed) {
            invalidate();
        }
        inDrawableStateChanged = false;
    }

    private void expandHint(boolean animate) {
        if (animator != null && animator.isRunning()) {
            animator.cancel();
        }
        if (animate && hintAnimationEnabled) {
            animateToExpansionFraction(0f);
        } else {
            collapsingTextHelper.setExpansionFraction(0f);
        }
        isHintExpanded = true;
    }

    private void animateToExpansionFraction(final float target) {
        if (collapsingTextHelper.getExpansionFraction() == target) {
            return;
        }
        if (animator == null) {
            animator = ViewUtils.createAnimator();
            animator.setInterpolator(AnimationUtils.LINEAR_INTERPOLATOR);
            animator.setDuration(ANIMATION_DURATION);
            animator.addUpdateListener(new ValueAnimatorCompat.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimatorCompat animator) {
                    collapsingTextHelper.setExpansionFraction(animator.getAnimatedFloatValue());
                }
            });
        }
        animator.setFloatValues(collapsingTextHelper.getExpansionFraction(), target);
        animator.start();
    }

    @VisibleForTesting
    final boolean isHintExpanded() {
        return isHintExpanded;
    }

    private class TextInputAccessibilityDelegate extends AccessibilityDelegateCompat {
        TextInputAccessibilityDelegate() {
        }

        @Override
        public void onInitializeAccessibilityEvent(View host, AccessibilityEvent event) {
            super.onInitializeAccessibilityEvent(host, event);
            event.setClassName(BufferTextInputLayout.class.getSimpleName());
        }

        @Override
        public void onPopulateAccessibilityEvent(View host, AccessibilityEvent event) {
            super.onPopulateAccessibilityEvent(host, event);
            final CharSequence text = collapsingTextHelper.getText();
            if (!TextUtils.isEmpty(text)) {
                event.getText().add(text);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName(BufferTextInputLayout.class.getSimpleName());
            final CharSequence text = collapsingTextHelper.getText();
            if (!TextUtils.isEmpty(text)) {
                info.setText(text);
            }
            if (editText != null) {
                info.setLabelFor(editText);
            }
            final CharSequence error = errorView != null ? errorView.getText() : null;
            if (!TextUtils.isEmpty(error)) {
                info.setContentInvalid(true);
                info.setError(error);
            }
        }
    }

    private static boolean arrayContains(int[] array, int value) {
        for (int v : array) {
            if (v == value) {
                return true;
            }
        }
        return false;
    }
}