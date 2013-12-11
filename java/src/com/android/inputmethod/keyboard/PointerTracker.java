/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.inputmethod.keyboard;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;

import com.android.inputmethod.accessibility.AccessibilityUtils;
import com.android.inputmethod.keyboard.internal.GestureStroke;
import com.android.inputmethod.keyboard.internal.GestureStroke.GestureStrokeParams;
import com.android.inputmethod.keyboard.internal.GestureStrokeWithPreviewPoints;
import com.android.inputmethod.keyboard.internal.GestureStrokeWithPreviewPoints.GestureStrokePreviewParams;
import com.android.inputmethod.keyboard.internal.PointerTrackerQueue;
import com.android.inputmethod.latin.Constants;
import com.android.inputmethod.latin.InputPointers;
import com.android.inputmethod.latin.LatinImeLogger;
import com.android.inputmethod.latin.R;
import com.android.inputmethod.latin.define.ProductionFlag;
import com.android.inputmethod.latin.settings.Settings;
import com.android.inputmethod.latin.utils.CollectionUtils;
import com.android.inputmethod.latin.utils.CoordinateUtils;
import com.android.inputmethod.latin.utils.ResourceUtils;
import com.android.inputmethod.research.ResearchLogger;

import java.util.ArrayList;

public final class PointerTracker implements PointerTrackerQueue.Element {
    private static final String TAG = PointerTracker.class.getSimpleName();
    private static final boolean DEBUG_EVENT = false;
    private static final boolean DEBUG_MOVE_EVENT = false;
    private static final boolean DEBUG_LISTENER = false;
    private static boolean DEBUG_MODE = LatinImeLogger.sDBG || DEBUG_EVENT;

    /** True if {@link PointerTracker}s should handle gesture events. */
    private static boolean sShouldHandleGesture = false;
    private static boolean sMainDictionaryAvailable = false;
    private static boolean sGestureHandlingEnabledByInputField = false;
    private static boolean sGestureHandlingEnabledByUser = false;

    public interface DrawingProxy {
        public void invalidateKey(Key key);
        public void showKeyPreview(Key key);
        public void dismissKeyPreview(Key key);
        public void showSlidingKeyInputPreview(PointerTracker tracker);
        public void dismissSlidingKeyInputPreview();
        public void showGestureTrail(PointerTracker tracker, boolean showsFloatingPreviewText);
    }

    public interface TimerProxy {
        public void startTypingStateTimer(Key typedKey);
        public boolean isTypingState();
        public void startKeyRepeatTimerOf(PointerTracker tracker, int repeatCount, int delay);
        public void startLongPressTimerOf(PointerTracker tracker, int delay);
        public void cancelLongPressTimerOf(PointerTracker tracker);
        public void cancelKeyTimersOf(PointerTracker tracker);
        public void startDoubleTapShiftKeyTimer();
        public void cancelDoubleTapShiftKeyTimer();
        public boolean isInDoubleTapShiftKeyTimeout();
        public void startUpdateBatchInputTimer(PointerTracker tracker);
        public void cancelUpdateBatchInputTimer(PointerTracker tracker);
        public void cancelAllUpdateBatchInputTimers();

        public static class Adapter implements TimerProxy {
            @Override
            public void startTypingStateTimer(Key typedKey) {}
            @Override
            public boolean isTypingState() { return false; }
            @Override
            public void startKeyRepeatTimerOf(PointerTracker tracker, int repeatCount, int delay) {}
            @Override
            public void startLongPressTimerOf(PointerTracker tracker, int delay) {}
            @Override
            public void cancelLongPressTimerOf(PointerTracker tracker) {}
            @Override
            public void cancelKeyTimersOf(PointerTracker tracker) {}
            @Override
            public void startDoubleTapShiftKeyTimer() {}
            @Override
            public void cancelDoubleTapShiftKeyTimer() {}
            @Override
            public boolean isInDoubleTapShiftKeyTimeout() { return false; }
            @Override
            public void startUpdateBatchInputTimer(PointerTracker tracker) {}
            @Override
            public void cancelUpdateBatchInputTimer(PointerTracker tracker) {}
            @Override
            public void cancelAllUpdateBatchInputTimers() {}
        }
    }

    static final class PointerTrackerParams {
        public final boolean mKeySelectionByDraggingFinger;
        public final int mTouchNoiseThresholdTime;
        public final int mTouchNoiseThresholdDistance;
        public final int mSuppressKeyPreviewAfterBatchInputDuration;
        public final int mKeyRepeatStartTimeout;
        public final int mKeyRepeatInterval;
        public final int mLongPressShiftLockTimeout;

        public PointerTrackerParams(final TypedArray mainKeyboardViewAttr) {
            mKeySelectionByDraggingFinger = mainKeyboardViewAttr.getBoolean(
                    R.styleable.MainKeyboardView_keySelectionByDraggingFinger, false);
            mTouchNoiseThresholdTime = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_touchNoiseThresholdTime, 0);
            mTouchNoiseThresholdDistance = mainKeyboardViewAttr.getDimensionPixelSize(
                    R.styleable.MainKeyboardView_touchNoiseThresholdDistance, 0);
            mSuppressKeyPreviewAfterBatchInputDuration = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_suppressKeyPreviewAfterBatchInputDuration, 0);
            mKeyRepeatStartTimeout = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_keyRepeatStartTimeout, 0);
            mKeyRepeatInterval = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_keyRepeatInterval, 0);
            mLongPressShiftLockTimeout = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_longPressShiftLockTimeout, 0);
        }
    }

    // Parameters for pointer handling.
    private static PointerTrackerParams sParams;
    private static GestureStrokeParams sGestureStrokeParams;
    private static GestureStrokePreviewParams sGesturePreviewParams;
    private static boolean sNeedsPhantomSuddenMoveEventHack;
    // Move this threshold to resource.
    // TODO: Device specific parameter would be better for device specific hack?
    private static final float PHANTOM_SUDDEN_MOVE_THRESHOLD = 0.25f; // in keyWidth
    // This hack is applied to certain classes of tablets.
    // See {@link #needsProximateBogusDownMoveUpEventHack(Resources)}.
    private static boolean sNeedsProximateBogusDownMoveUpEventHack;

    private static final ArrayList<PointerTracker> sTrackers = CollectionUtils.newArrayList();
    private static final PointerTrackerQueue sPointerTrackerQueue = new PointerTrackerQueue();

    public final int mPointerId;

    private static DrawingProxy sDrawingProxy;
    private static TimerProxy sTimerProxy;
    private static KeyDetector sDefaultKeyDetector;
    private KeyDetector mKeyDetector;
    private static KeyboardActionListener sListener = KeyboardActionListener.EMPTY_LISTENER;

    private Keyboard mKeyboard;
    private int mPhantomSuddenMoveThreshold;
    private final BogusMoveEventDetector mBogusMoveEventDetector = new BogusMoveEventDetector();

    private boolean mIsDetectingGesture = false; // per PointerTracker.
    private static boolean sInGesture = false;
    private static long sGestureFirstDownTime;
    private static TimeRecorder sTimeRecorder;
    private static final InputPointers sAggregratedPointers = new InputPointers(
            GestureStroke.DEFAULT_CAPACITY);
    private static int sLastRecognitionPointSize = 0; // synchronized using sAggregratedPointers
    private static long sLastRecognitionTime = 0; // synchronized using sAggregratedPointers

    static final class BogusMoveEventDetector {
        // Move these thresholds to resource.
        // These thresholds' unit is a diagonal length of a key.
        private static final float BOGUS_MOVE_ACCUMULATED_DISTANCE_THRESHOLD = 0.53f;
        private static final float BOGUS_MOVE_RADIUS_THRESHOLD = 1.14f;

        private int mAccumulatedDistanceThreshold;
        private int mRadiusThreshold;

        // Accumulated distance from actual and artificial down keys.
        /* package */ int mAccumulatedDistanceFromDownKey;
        private int mActualDownX;
        private int mActualDownY;

        public void setKeyboardGeometry(final int keyWidth, final int keyHeight) {
            final float keyDiagonal = (float)Math.hypot(keyWidth, keyHeight);
            mAccumulatedDistanceThreshold = (int)(
                    keyDiagonal * BOGUS_MOVE_ACCUMULATED_DISTANCE_THRESHOLD);
            mRadiusThreshold = (int)(keyDiagonal * BOGUS_MOVE_RADIUS_THRESHOLD);
        }

        public void onActualDownEvent(final int x, final int y) {
            mActualDownX = x;
            mActualDownY = y;
        }

        public void onDownKey() {
            mAccumulatedDistanceFromDownKey = 0;
        }

        public void onMoveKey(final int distance) {
            mAccumulatedDistanceFromDownKey += distance;
        }

        public boolean hasTraveledLongDistance(final int x, final int y) {
            final int dx = Math.abs(x - mActualDownX);
            final int dy = Math.abs(y - mActualDownY);
            // A bogus move event should be a horizontal movement. A vertical movement might be
            // a sloppy typing and should be ignored.
            return dx >= dy && mAccumulatedDistanceFromDownKey >= mAccumulatedDistanceThreshold;
        }

        /* package */ int getDistanceFromDownEvent(final int x, final int y) {
            return getDistance(x, y, mActualDownX, mActualDownY);
        }

        public boolean isCloseToActualDownEvent(final int x, final int y) {
            return getDistanceFromDownEvent(x, y) < mRadiusThreshold;
        }
    }

    static final class TimeRecorder {
        private final int mSuppressKeyPreviewAfterBatchInputDuration;
        private final int mStaticTimeThresholdAfterFastTyping; // msec
        private long mLastTypingTime;
        private long mLastLetterTypingTime;
        private long mLastBatchInputTime;

        public TimeRecorder(final PointerTrackerParams pointerTrackerParams,
                final GestureStrokeParams gestureStrokeParams) {
            mSuppressKeyPreviewAfterBatchInputDuration =
                    pointerTrackerParams.mSuppressKeyPreviewAfterBatchInputDuration;
            mStaticTimeThresholdAfterFastTyping =
                    gestureStrokeParams.mStaticTimeThresholdAfterFastTyping;
        }

        public boolean isInFastTyping(final long eventTime) {
            final long elapsedTimeSinceLastLetterTyping = eventTime - mLastLetterTypingTime;
            return elapsedTimeSinceLastLetterTyping < mStaticTimeThresholdAfterFastTyping;
        }

        private boolean wasLastInputTyping() {
            return mLastTypingTime >= mLastBatchInputTime;
        }

        public void onCodeInput(final int code, final long eventTime) {
            // Record the letter typing time when
            // 1. Letter keys are typed successively without any batch input in between.
            // 2. A letter key is typed within the threshold time since the last any key typing.
            // 3. A non-letter key is typed within the threshold time since the last letter key
            // typing.
            if (Character.isLetter(code)) {
                if (wasLastInputTyping()
                        || eventTime - mLastTypingTime < mStaticTimeThresholdAfterFastTyping) {
                    mLastLetterTypingTime = eventTime;
                }
            } else {
                if (eventTime - mLastLetterTypingTime < mStaticTimeThresholdAfterFastTyping) {
                    // This non-letter typing should be treated as a part of fast typing.
                    mLastLetterTypingTime = eventTime;
                }
            }
            mLastTypingTime = eventTime;
        }

        public void onEndBatchInput(final long eventTime) {
            mLastBatchInputTime = eventTime;
        }

        public long getLastLetterTypingTime() {
            return mLastLetterTypingTime;
        }

        public boolean needsToSuppressKeyPreviewPopup(final long eventTime) {
            return !wasLastInputTyping()
                    && eventTime - mLastBatchInputTime < mSuppressKeyPreviewAfterBatchInputDuration;
        }
    }

    // The position and time at which first down event occurred.
    private long mDownTime;
    private int[] mDownCoordinates = CoordinateUtils.newInstance();
    private long mUpTime;

    // The current key where this pointer is.
    private Key mCurrentKey = null;
    // The position where the current key was recognized for the first time.
    private int mKeyX;
    private int mKeyY;

    // Last pointer position.
    private int mLastX;
    private int mLastY;

    // true if keyboard layout has been changed.
    private boolean mKeyboardLayoutHasBeenChanged;

    // true if this pointer is no longer triggering any action because it has been canceled.
    private boolean mIsTrackingForActionDisabled;

    // the more keys panel currently being shown. equals null if no panel is active.
    private MoreKeysPanel mMoreKeysPanel;

    private static final int MULTIPLIER_FOR_LONG_PRESS_TIMEOUT_IN_SLIDING_INPUT = 3;
    // true if this pointer is in the dragging finger mode.
    boolean mIsInDraggingFinger;
    // true if this pointer is sliding from a modifier key and in the sliding key input mode,
    // so that further modifier keys should be ignored.
    boolean mIsInSlidingKeyInput;
    // if not a NOT_A_CODE, the key of this code is repeating
    private int mCurrentRepeatingKeyCode = Constants.NOT_A_CODE;

    // true if dragging finger is allowed.
    private boolean mIsAllowedDraggingFinger;

    private final GestureStrokeWithPreviewPoints mGestureStrokeWithPreviewPoints;

    private static final int SMALL_TABLET_SMALLEST_WIDTH = 600; // dp
    private static final int LARGE_TABLET_SMALLEST_WIDTH = 768; // dp

    private static boolean needsProximateBogusDownMoveUpEventHack(final Resources res) {
        // The proximate bogus down move up event hack is needed for a device such like,
        // 1) is large tablet, or 2) is small tablet and the screen density is less than hdpi.
        // Though it seems odd to use screen density as criteria of the quality of the touch
        // screen, the small table that has a less density screen than hdpi most likely has been
        // made with the touch screen that needs the hack.
        final int sw = res.getConfiguration().smallestScreenWidthDp;
        final boolean isLargeTablet = (sw >= LARGE_TABLET_SMALLEST_WIDTH);
        final boolean isSmallTablet =
                (sw >= SMALL_TABLET_SMALLEST_WIDTH && sw < LARGE_TABLET_SMALLEST_WIDTH);
        final int densityDpi = res.getDisplayMetrics().densityDpi;
        final boolean hasLowDensityScreen = (densityDpi < DisplayMetrics.DENSITY_HIGH);
        final boolean needsTheHack = isLargeTablet || (isSmallTablet && hasLowDensityScreen);
        if (DEBUG_MODE) {
            Log.d(TAG, "needsProximateBogusDownMoveUpEventHack=" + needsTheHack
                    + " smallestScreenWidthDp=" + sw + " densityDpi=" + densityDpi);
        }
        return needsTheHack;
    }

    // TODO: Add PointerTrackerFactory singleton and move some class static methods into it.
    public static void init(final TypedArray mainKeyboardViewAttr, final TimerProxy timerProxy,
            final DrawingProxy drawingProxy, final KeyDetector defaultKeyDetector) {
        sParams = new PointerTrackerParams(mainKeyboardViewAttr);
        sGestureStrokeParams = new GestureStrokeParams(mainKeyboardViewAttr);
        sGesturePreviewParams = new GestureStrokePreviewParams(mainKeyboardViewAttr);
        sTimeRecorder = new TimeRecorder(sParams, sGestureStrokeParams);

        final Resources res = mainKeyboardViewAttr.getResources();
        sNeedsPhantomSuddenMoveEventHack = Boolean.parseBoolean(
                ResourceUtils.getDeviceOverrideValue(
                        res, R.array.phantom_sudden_move_event_device_list));
        sNeedsProximateBogusDownMoveUpEventHack = needsProximateBogusDownMoveUpEventHack(res);
        sGestureStrokeParams = GestureStrokeParams.DEFAULT;
        sGesturePreviewParams = GestureStrokePreviewParams.DEFAULT;
        sTimeRecorder = new TimeRecorder(sParams, sGestureStrokeParams);

        sTimerProxy = timerProxy;
        sDrawingProxy = drawingProxy;
        sDefaultKeyDetector = defaultKeyDetector;
    }

    private static void updateGestureHandlingMode() {
        sShouldHandleGesture = sMainDictionaryAvailable
                && sGestureHandlingEnabledByInputField
                && sGestureHandlingEnabledByUser
                && !AccessibilityUtils.getInstance().isTouchExplorationEnabled();
    }

    // Note that this method is called from a non-UI thread.
    public static void setMainDictionaryAvailability(final boolean mainDictionaryAvailable) {
        sMainDictionaryAvailable = mainDictionaryAvailable;
        updateGestureHandlingMode();
    }

    public static void setGestureHandlingEnabledByUser(final boolean gestureHandlingEnabledByUser) {
        sGestureHandlingEnabledByUser = gestureHandlingEnabledByUser;
        updateGestureHandlingMode();
    }

    public static PointerTracker getPointerTracker(final int id) {
        final ArrayList<PointerTracker> trackers = sTrackers;

        // Create pointer trackers until we can get 'id+1'-th tracker, if needed.
        for (int i = trackers.size(); i <= id; i++) {
            final PointerTracker tracker = new PointerTracker(i);
            trackers.add(tracker);
        }

        return trackers.get(id);
    }

    public static boolean isAnyInDraggingFinger() {
        return sPointerTrackerQueue.isAnyInDraggingFinger();
    }

    public static void cancelAllPointerTrackers() {
        sPointerTrackerQueue.cancelAllPointerTrackers();
    }

    public static void setKeyboardActionListener(final KeyboardActionListener listener) {
        sListener = listener;
    }

    public static void setKeyDetector(final KeyDetector keyDetector) {
        final int trackersSize = sTrackers.size();
        for (int i = 0; i < trackersSize; ++i) {
            final PointerTracker tracker = sTrackers.get(i);
            tracker.setKeyDetectorInner(keyDetector);
        }
        final Keyboard keyboard = keyDetector.getKeyboard();
        sGestureHandlingEnabledByInputField = !keyboard.mId.passwordInput();
        updateGestureHandlingMode();
    }

    public static void setReleasedKeyGraphicsToAllKeys() {
        final int trackersSize = sTrackers.size();
        for (int i = 0; i < trackersSize; ++i) {
            final PointerTracker tracker = sTrackers.get(i);
            tracker.setReleasedKeyGraphics(tracker.getKey());
        }
    }

    public static void dismissAllMoreKeysPanels() {
        final int trackersSize = sTrackers.size();
        for (int i = 0; i < trackersSize; ++i) {
            final PointerTracker tracker = sTrackers.get(i);
            tracker.dismissMoreKeysPanel();
        }
    }

    private PointerTracker(final int id) {
        mPointerId = id;
        mGestureStrokeWithPreviewPoints = new GestureStrokeWithPreviewPoints(
                id, sGestureStrokeParams, sGesturePreviewParams);
        setKeyDetectorInner(sDefaultKeyDetector);
    }

    // Returns true if keyboard has been changed by this callback.
    private boolean callListenerOnPressAndCheckKeyboardLayoutChange(final Key key,
            final int repeatCount) {
        // While gesture input is going on, this method should be a no-operation. But when gesture
        // input has been canceled, <code>sInGesture</code> and <code>mIsDetectingGesture</code>
        // are set to false. To keep this method is a no-operation,
        // <code>mIsTrackingForActionDisabled</code> should also be taken account of.
        if (sInGesture || mIsDetectingGesture || mIsTrackingForActionDisabled) {
            return false;
        }
        final boolean ignoreModifierKey = mIsInDraggingFinger && key.isModifier();
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format("[%d] onPress    : %s%s%s%s", mPointerId,
                    KeyDetector.printableCode(key),
                    ignoreModifierKey ? " ignoreModifier" : "",
                    key.isEnabled() ? "" : " disabled",
                    repeatCount > 0 ? " repeatCount=" + repeatCount : ""));
        }
        if (ignoreModifierKey) {
            return false;
        }
        if (key.isEnabled()) {
            sListener.onPressKey(key.getCode(), repeatCount, getActivePointerTrackerCount() == 1);
            final boolean keyboardLayoutHasBeenChanged = mKeyboardLayoutHasBeenChanged;
            mKeyboardLayoutHasBeenChanged = false;
            sTimerProxy.startTypingStateTimer(key);
            return keyboardLayoutHasBeenChanged;
        }
        return false;
    }

    // Note that we need primaryCode argument because the keyboard may in shifted state and the
    // primaryCode is different from {@link Key#mCode}.
    private void callListenerOnCodeInput(final Key key, final int primaryCode, final int x,
            final int y, final long eventTime) {
        final boolean ignoreModifierKey = mIsInDraggingFinger && key.isModifier();
        final boolean altersCode = key.altCodeWhileTyping() && sTimerProxy.isTypingState();
        final int code = altersCode ? key.getAltCode() : primaryCode;
        if (DEBUG_LISTENER) {
            final String output = code == Constants.CODE_OUTPUT_TEXT
                    ? key.getOutputText() : Constants.printableCode(code);
            Log.d(TAG, String.format("[%d] onCodeInput: %4d %4d %s%s%s", mPointerId, x, y,
                    output, ignoreModifierKey ? " ignoreModifier" : "",
                    altersCode ? " altersCode" : "", key.isEnabled() ? "" : " disabled"));
        }
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.pointerTracker_callListenerOnCodeInput(key, x, y, ignoreModifierKey,
                    altersCode, code);
        }
        if (ignoreModifierKey) {
            return;
        }
        // Even if the key is disabled, it should respond if it is in the altCodeWhileTyping state.
        if (key.isEnabled() || altersCode) {
            sTimeRecorder.onCodeInput(code, eventTime);
            if (code == Constants.CODE_OUTPUT_TEXT) {
                sListener.onTextInput(key.getOutputText());
            } else if (code != Constants.CODE_UNSPECIFIED) {
                sListener.onCodeInput(code, x, y);
            }
        }
    }

    // Note that we need primaryCode argument because the keyboard may be in shifted state and the
    // primaryCode is different from {@link Key#mCode}.
    private void callListenerOnRelease(final Key key, final int primaryCode,
            final boolean withSliding) {
        // See the comment at {@link #callListenerOnPressAndCheckKeyboardLayoutChange(Key}}.
        if (sInGesture || mIsDetectingGesture || mIsTrackingForActionDisabled) {
            return;
        }
        final boolean ignoreModifierKey = mIsInDraggingFinger && key.isModifier();
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format("[%d] onRelease  : %s%s%s%s", mPointerId,
                    Constants.printableCode(primaryCode),
                    withSliding ? " sliding" : "", ignoreModifierKey ? " ignoreModifier" : "",
                    key.isEnabled() ?  "": " disabled"));
        }
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.pointerTracker_callListenerOnRelease(key, primaryCode, withSliding,
                    ignoreModifierKey);
        }
        if (ignoreModifierKey) {
            return;
        }
        if (key.isEnabled()) {
            sListener.onReleaseKey(primaryCode, withSliding);
        }
    }

    private void callListenerOnFinishSlidingInput() {
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format("[%d] onFinishSlidingInput", mPointerId));
        }
        sListener.onFinishSlidingInput();
    }

    private void callListenerOnCancelInput() {
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format("[%d] onCancelInput", mPointerId));
        }
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.pointerTracker_callListenerOnCancelInput();
        }
        sListener.onCancelInput();
    }

    private void setKeyDetectorInner(final KeyDetector keyDetector) {
        final Keyboard keyboard = keyDetector.getKeyboard();
        if (keyDetector == mKeyDetector && keyboard == mKeyboard) {
            return;
        }
        mKeyDetector = keyDetector;
        mKeyboard = keyDetector.getKeyboard();
        // Mark that keyboard layout has been changed.
        mKeyboardLayoutHasBeenChanged = true;
        final int keyWidth = mKeyboard.mMostCommonKeyWidth;
        final int keyHeight = mKeyboard.mMostCommonKeyHeight;
        mGestureStrokeWithPreviewPoints.setKeyboardGeometry(keyWidth, mKeyboard.mOccupiedHeight);
        final Key newKey = mKeyDetector.detectHitKey(mKeyX, mKeyY);
        if (newKey != mCurrentKey) {
            if (sDrawingProxy != null) {
                setReleasedKeyGraphics(mCurrentKey);
            }
            // Keep {@link #mCurrentKey} that comes from previous keyboard.
        }
        mPhantomSuddenMoveThreshold = (int)(keyWidth * PHANTOM_SUDDEN_MOVE_THRESHOLD);
        mBogusMoveEventDetector.setKeyboardGeometry(keyWidth, keyHeight);
    }

    @Override
    public boolean isInDraggingFinger() {
        return mIsInDraggingFinger;
    }

    public Key getKey() {
        return mCurrentKey;
    }

    @Override
    public boolean isModifier() {
        return mCurrentKey != null && mCurrentKey.isModifier();
    }

    public Key getKeyOn(final int x, final int y) {
        return mKeyDetector.detectHitKey(x, y);
    }

    private void setReleasedKeyGraphics(final Key key) {
        sDrawingProxy.dismissKeyPreview(key);
        if (key == null) {
            return;
        }

        // Even if the key is disabled, update the key release graphics just in case.
        updateReleaseKeyGraphics(key);

        if (key.isShift()) {
            for (final Key shiftKey : mKeyboard.mShiftKeys) {
                if (shiftKey != key) {
                    updateReleaseKeyGraphics(shiftKey);
                }
            }
        }

        if (key.altCodeWhileTyping()) {
            final int altCode = key.getAltCode();
            final Key altKey = mKeyboard.getKey(altCode);
            if (altKey != null) {
                updateReleaseKeyGraphics(altKey);
            }
            for (final Key k : mKeyboard.mAltCodeKeysWhileTyping) {
                if (k != key && k.getAltCode() == altCode) {
                    updateReleaseKeyGraphics(k);
                }
            }
        }
    }

    private static boolean needsToSuppressKeyPreviewPopup(final long eventTime) {
        if (!sShouldHandleGesture) return false;
        return sTimeRecorder.needsToSuppressKeyPreviewPopup(eventTime);
    }

    private void setPressedKeyGraphics(final Key key, final long eventTime) {
        if (key == null) {
            return;
        }

        // Even if the key is disabled, it should respond if it is in the altCodeWhileTyping state.
        final boolean altersCode = key.altCodeWhileTyping() && sTimerProxy.isTypingState();
        final boolean needsToUpdateGraphics = key.isEnabled() || altersCode;
        if (!needsToUpdateGraphics) {
            return;
        }

        if (!key.noKeyPreview() && !sInGesture && !needsToSuppressKeyPreviewPopup(eventTime)) {
            sDrawingProxy.showKeyPreview(key);
        }
        updatePressKeyGraphics(key);

        if (key.isShift()) {
            for (final Key shiftKey : mKeyboard.mShiftKeys) {
                if (shiftKey != key) {
                    updatePressKeyGraphics(shiftKey);
                }
            }
        }

        if (key.altCodeWhileTyping() && sTimerProxy.isTypingState()) {
            final int altCode = key.getAltCode();
            final Key altKey = mKeyboard.getKey(altCode);
            if (altKey != null) {
                updatePressKeyGraphics(altKey);
            }
            for (final Key k : mKeyboard.mAltCodeKeysWhileTyping) {
                if (k != key && k.getAltCode() == altCode) {
                    updatePressKeyGraphics(k);
                }
            }
        }
    }

    private static void updateReleaseKeyGraphics(final Key key) {
        key.onReleased();
        sDrawingProxy.invalidateKey(key);
    }

    private static void updatePressKeyGraphics(final Key key) {
        key.onPressed();
        sDrawingProxy.invalidateKey(key);
    }

    public GestureStrokeWithPreviewPoints getGestureStrokeWithPreviewPoints() {
        return mGestureStrokeWithPreviewPoints;
    }

    public void getLastCoordinates(final int[] outCoords) {
        CoordinateUtils.set(outCoords, mLastX, mLastY);
    }

    public long getDownTime() {
        return mDownTime;
    }

    public void getDownCoordinates(final int[] outCoords) {
        CoordinateUtils.copy(outCoords, mDownCoordinates);
    }

    private Key onDownKey(final int x, final int y, final long eventTime) {
        mDownTime = eventTime;
        CoordinateUtils.set(mDownCoordinates, x, y);
        mBogusMoveEventDetector.onDownKey();
        return onMoveToNewKey(onMoveKeyInternal(x, y), x, y);
    }

    static int getDistance(final int x1, final int y1, final int x2, final int y2) {
        return (int)Math.hypot(x1 - x2, y1 - y2);
    }

    private Key onMoveKeyInternal(final int x, final int y) {
        mBogusMoveEventDetector.onMoveKey(getDistance(x, y, mLastX, mLastY));
        mLastX = x;
        mLastY = y;
        return mKeyDetector.detectHitKey(x, y);
    }

    private Key onMoveKey(final int x, final int y) {
        return onMoveKeyInternal(x, y);
    }

    private Key onMoveToNewKey(final Key newKey, final int x, final int y) {
        mCurrentKey = newKey;
        mKeyX = x;
        mKeyY = y;
        return newKey;
    }

    /* package */ static int getActivePointerTrackerCount() {
        return sPointerTrackerQueue.size();
    }

    private boolean isOldestTrackerInQueue() {
        return sPointerTrackerQueue.getOldestElement() == this;
    }

    private void mayStartBatchInput(final Key key) {
        if (sInGesture || !mGestureStrokeWithPreviewPoints.isStartOfAGesture()) {
            return;
        }
        if (key == null || !Character.isLetter(key.getCode())) {
            return;
        }
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format("[%d] onStartBatchInput", mPointerId));
        }
        sInGesture = true;
        synchronized (sAggregratedPointers) {
            sAggregratedPointers.reset();
            sLastRecognitionPointSize = 0;
            sLastRecognitionTime = 0;
            sListener.onStartBatchInput();
            dismissAllMoreKeysPanels();
        }
        sTimerProxy.cancelLongPressTimerOf(this);
        // A gesture floating preview text will be shown at the oldest pointer/finger on the screen.
        sDrawingProxy.showGestureTrail(
                this, isOldestTrackerInQueue() /* showsFloatingPreviewText */);
    }

    public void updateBatchInputByTimer(final long eventTime) {
        final int gestureTime = (int)(eventTime - sGestureFirstDownTime);
        mGestureStrokeWithPreviewPoints.duplicateLastPointWith(gestureTime);
        updateBatchInput(eventTime);
    }

    private void mayUpdateBatchInput(final long eventTime, final Key key) {
        if (key != null) {
            updateBatchInput(eventTime);
        }
        if (mIsTrackingForActionDisabled) {
            return;
        }
        // A gesture floating preview text will be shown at the oldest pointer/finger on the screen.
        sDrawingProxy.showGestureTrail(
                this, isOldestTrackerInQueue() /* showsFloatingPreviewText */);
    }

    private void updateBatchInput(final long eventTime) {
        synchronized (sAggregratedPointers) {
            final GestureStroke stroke = mGestureStrokeWithPreviewPoints;
            stroke.appendIncrementalBatchPoints(sAggregratedPointers);
            final int size = sAggregratedPointers.getPointerSize();
            if (size > sLastRecognitionPointSize
                    && stroke.hasRecognitionTimePast(eventTime, sLastRecognitionTime)) {
                if (DEBUG_LISTENER) {
                    Log.d(TAG, String.format("[%d] onUpdateBatchInput: batchPoints=%d", mPointerId,
                            size));
                }
                sTimerProxy.startUpdateBatchInputTimer(this);
                sListener.onUpdateBatchInput(sAggregratedPointers);
                // The listener may change the size of the pointers (when auto-committing
                // for example), so we need to get the size from the pointers again.
                sLastRecognitionPointSize = sAggregratedPointers.getPointerSize();
                sLastRecognitionTime = eventTime;
            }
        }
    }

    private void mayEndBatchInput(final long eventTime) {
        synchronized (sAggregratedPointers) {
            mGestureStrokeWithPreviewPoints.appendAllBatchPoints(sAggregratedPointers);
            if (getActivePointerTrackerCount() == 1) {
                sInGesture = false;
                sTimeRecorder.onEndBatchInput(eventTime);
                sTimerProxy.cancelAllUpdateBatchInputTimers();
                if (!mIsTrackingForActionDisabled) {
                    if (DEBUG_LISTENER) {
                        Log.d(TAG, String.format("[%d] onEndBatchInput   : batchPoints=%d",
                                mPointerId, sAggregratedPointers.getPointerSize()));
                    }
                    sListener.onEndBatchInput(sAggregratedPointers);
                }
            }
        }
        if (mIsTrackingForActionDisabled) {
            return;
        }
        // A gesture floating preview text will be shown at the oldest pointer/finger on the screen.
        sDrawingProxy.showGestureTrail(
                this, isOldestTrackerInQueue() /* showsFloatingPreviewText */);
    }

    private void cancelBatchInput() {
        cancelAllPointerTrackers();
        mIsDetectingGesture = false;
        if (!sInGesture) {
            return;
        }
        sInGesture = false;
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format("[%d] onCancelBatchInput", mPointerId));
        }
        sListener.onCancelBatchInput();
    }

    public void processMotionEvent(final MotionEvent me, final KeyDetector keyDetector) {
        final int action = me.getActionMasked();
        final long eventTime = me.getEventTime();
        if (action == MotionEvent.ACTION_MOVE) {
            // When this pointer is the only active pointer and is showing a more keys panel,
            // we should ignore other pointers' motion event.
            final boolean shouldIgnoreOtherPointers =
                    isShowingMoreKeysPanel() && getActivePointerTrackerCount() == 1;
            final int pointerCount = me.getPointerCount();
            for (int index = 0; index < pointerCount; index++) {
                final int id = me.getPointerId(index);
                if (shouldIgnoreOtherPointers && id != mPointerId) {
                    continue;
                }
                final int x = (int)me.getX(index);
                final int y = (int)me.getY(index);
                final PointerTracker tracker = getPointerTracker(id);
                tracker.onMoveEvent(x, y, eventTime, me);
            }
            return;
        }
        final int index = me.getActionIndex();
        final int x = (int)me.getX(index);
        final int y = (int)me.getY(index);
        switch (action) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN:
            onDownEvent(x, y, eventTime, keyDetector);
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP:
            onUpEvent(x, y, eventTime);
            break;
        case MotionEvent.ACTION_CANCEL:
            onCancelEvent(x, y, eventTime);
            break;
        }
    }

    private void onDownEvent(final int x, final int y, final long eventTime,
            final KeyDetector keyDetector) {
        if (DEBUG_EVENT) {
            printTouchEvent("onDownEvent:", x, y, eventTime);
        }
        setKeyDetectorInner(keyDetector);
        // Naive up-to-down noise filter.
        final long deltaT = eventTime - mUpTime;
        if (deltaT < sParams.mTouchNoiseThresholdTime) {
            final int distance = getDistance(x, y, mLastX, mLastY);
            if (distance < sParams.mTouchNoiseThresholdDistance) {
                if (DEBUG_MODE)
                    Log.w(TAG, String.format("[%d] onDownEvent:"
                            + " ignore potential noise: time=%d distance=%d",
                            mPointerId, deltaT, distance));
                if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
                    ResearchLogger.pointerTracker_onDownEvent(deltaT, distance * distance);
                }
                cancelTrackingForAction();
                return;
            }
        }

        final Key key = getKeyOn(x, y);
        mBogusMoveEventDetector.onActualDownEvent(x, y);
        if (key != null && key.isModifier()) {
            // Before processing a down event of modifier key, all pointers already being
            // tracked should be released.
            sPointerTrackerQueue.releaseAllPointers(eventTime);
        }
        sPointerTrackerQueue.add(this);
        onDownEventInternal(x, y, eventTime);
        if (!sShouldHandleGesture) {
            return;
        }
        // A gesture should start only from a non-modifier key. Note that the gesture detection is
        // disabled when the key is repeating.
        mIsDetectingGesture = (mKeyboard != null) && mKeyboard.mId.isAlphabetKeyboard()
                && key != null && !key.isModifier();
        if (mIsDetectingGesture) {
            if (getActivePointerTrackerCount() == 1) {
                sGestureFirstDownTime = eventTime;
            }
            mGestureStrokeWithPreviewPoints.onDownEvent(x, y, eventTime, sGestureFirstDownTime,
                    sTimeRecorder.getLastLetterTypingTime());
        }
    }

    /* package */ boolean isShowingMoreKeysPanel() {
        return (mMoreKeysPanel != null);
    }

    private void dismissMoreKeysPanel() {
        if (isShowingMoreKeysPanel()) {
            mMoreKeysPanel.dismissMoreKeysPanel();
            mMoreKeysPanel = null;
        }
    }

    private void onDownEventInternal(final int x, final int y, final long eventTime) {
        Key key = onDownKey(x, y, eventTime);
        // Key selection by dragging finger is allowed when 1) key selection by dragging finger is
        // enabled by configuration, 2) this pointer starts dragging from modifier key, or 3) this
        // pointer's KeyDetector always allows key selection by dragging finger, such as
        // {@link MoreKeysKeyboard}.
        mIsAllowedDraggingFinger = sParams.mKeySelectionByDraggingFinger
                || (key != null && key.isModifier())
                || mKeyDetector.alwaysAllowsKeySelectionByDraggingFinger();
        mKeyboardLayoutHasBeenChanged = false;
        mIsTrackingForActionDisabled = false;
        resetKeySelectionByDraggingFinger();
        if (key != null) {
            // This onPress call may have changed keyboard layout. Those cases are detected at
            // {@link #setKeyboard}. In those cases, we should update key according to the new
            // keyboard layout.
            if (callListenerOnPressAndCheckKeyboardLayoutChange(key, 0 /* repeatCount */)) {
                key = onDownKey(x, y, eventTime);
            }

            startRepeatKey(key);
            startLongPressTimer(key);
            setPressedKeyGraphics(key, eventTime);
        }
    }

    private void startKeySelectionByDraggingFinger(final Key key) {
        if (!mIsInDraggingFinger) {
            mIsInSlidingKeyInput = key.isModifier();
        }
        mIsInDraggingFinger = true;
    }

    private void resetKeySelectionByDraggingFinger() {
        mIsInDraggingFinger = false;
        mIsInSlidingKeyInput = false;
        sDrawingProxy.dismissSlidingKeyInputPreview();
    }

    private void onGestureMoveEvent(final int x, final int y, final long eventTime,
            final boolean isMajorEvent, final Key key) {
        final int gestureTime = (int)(eventTime - sGestureFirstDownTime);
        if (mIsDetectingGesture) {
            final int beforeLength = mGestureStrokeWithPreviewPoints.getLength();
            final boolean onValidArea = mGestureStrokeWithPreviewPoints.addPointOnKeyboard(
                    x, y, gestureTime, isMajorEvent);
            if (mGestureStrokeWithPreviewPoints.getLength() > beforeLength) {
                sTimerProxy.startUpdateBatchInputTimer(this);
            }
            // If the move event goes out from valid batch input area, cancel batch input.
            if (!onValidArea) {
                cancelBatchInput();
                return;
            }
            // If the MoreKeysPanel is showing then do not attempt to enter gesture mode. However,
            // the gestured touch points are still being recorded in case the panel is dismissed.
            if (isShowingMoreKeysPanel()) {
                return;
            }
            mayStartBatchInput(key);
            if (sInGesture) {
                mayUpdateBatchInput(eventTime, key);
            }
        }
    }

    private void onMoveEvent(final int x, final int y, final long eventTime, final MotionEvent me) {
        if (DEBUG_MOVE_EVENT) {
            printTouchEvent("onMoveEvent:", x, y, eventTime);
        }
        if (mIsTrackingForActionDisabled) {
            return;
        }

        if (sShouldHandleGesture && me != null) {
            // Add historical points to gesture path.
            final int pointerIndex = me.findPointerIndex(mPointerId);
            final int historicalSize = me.getHistorySize();
            for (int h = 0; h < historicalSize; h++) {
                final int historicalX = (int)me.getHistoricalX(pointerIndex, h);
                final int historicalY = (int)me.getHistoricalY(pointerIndex, h);
                final long historicalTime = me.getHistoricalEventTime(h);
                onGestureMoveEvent(historicalX, historicalY, historicalTime,
                        false /* isMajorEvent */, null);
            }
        }

        if (isShowingMoreKeysPanel()) {
            final int translatedX = mMoreKeysPanel.translateX(x);
            final int translatedY = mMoreKeysPanel.translateY(y);
            mMoreKeysPanel.onMoveEvent(translatedX, translatedY, mPointerId, eventTime);
            onMoveKey(x, y);
            if (mIsInSlidingKeyInput) {
                sDrawingProxy.showSlidingKeyInputPreview(this);
            }
            return;
        }
        onMoveEventInternal(x, y, eventTime);
    }

    private void processDraggingFingerInToNewKey(final Key newKey, final int x, final int y,
            final long eventTime) {
        // This onPress call may have changed keyboard layout. Those cases are detected
        // at {@link #setKeyboard}. In those cases, we should update key according
        // to the new keyboard layout.
        Key key = newKey;
        if (callListenerOnPressAndCheckKeyboardLayoutChange(key, 0 /* repeatCount */)) {
            key = onMoveKey(x, y);
        }
        onMoveToNewKey(key, x, y);
        if (mIsTrackingForActionDisabled) {
            return;
        }
        startLongPressTimer(key);
        setPressedKeyGraphics(key, eventTime);
    }

    private void processPhantomSuddenMoveHack(final Key key, final int x, final int y,
            final long eventTime, final Key oldKey, final int lastX, final int lastY) {
        if (DEBUG_MODE) {
            Log.w(TAG, String.format("[%d] onMoveEvent:"
                    + " phantom sudden move event (distance=%d) is translated to "
                    + "up[%d,%d,%s]/down[%d,%d,%s] events", mPointerId,
                    getDistance(x, y, lastX, lastY),
                    lastX, lastY, Constants.printableCode(oldKey.getCode()),
                    x, y, Constants.printableCode(key.getCode())));
        }
        // TODO: This should be moved to outside of this nested if-clause?
        if (ProductionFlag.USES_DEVELOPMENT_ONLY_DIAGNOSTICS) {
            ResearchLogger.pointerTracker_onMoveEvent(x, y, lastX, lastY);
        }
        onUpEventInternal(x, y, eventTime);
        onDownEventInternal(x, y, eventTime);
    }

    private void processProximateBogusDownMoveUpEventHack(final Key key, final int x, final int y,
            final long eventTime, final Key oldKey, final int lastX, final int lastY) {
        if (DEBUG_MODE) {
            final float keyDiagonal = (float)Math.hypot(
                    mKeyboard.mMostCommonKeyWidth, mKeyboard.mMostCommonKeyHeight);
            final float radiusRatio =
                    mBogusMoveEventDetector.getDistanceFromDownEvent(x, y)
                    / keyDiagonal;
            Log.w(TAG, String.format("[%d] onMoveEvent:"
                    + " bogus down-move-up event (raidus=%.2f key diagonal) is "
                    + " translated to up[%d,%d,%s]/down[%d,%d,%s] events",
                    mPointerId, radiusRatio,
                    lastX, lastY, Constants.printableCode(oldKey.getCode()),
                    x, y, Constants.printableCode(key.getCode())));
        }
        onUpEventInternal(x, y, eventTime);
        onDownEventInternal(x, y, eventTime);
    }

    private void processDraggingFingerOutFromOldKey(final Key oldKey) {
        setReleasedKeyGraphics(oldKey);
        callListenerOnRelease(oldKey, oldKey.getCode(), true /* withSliding */);
        startKeySelectionByDraggingFinger(oldKey);
        sTimerProxy.cancelKeyTimersOf(this);
    }

    private void dragFingerFromOldKeyToNewKey(final Key key, final int x, final int y,
            final long eventTime, final Key oldKey, final int lastX, final int lastY) {
        // The pointer has been slid in to the new key from the previous key, we must call
        // onRelease() first to notify that the previous key has been released, then call
        // onPress() to notify that the new key is being pressed.
        processDraggingFingerOutFromOldKey(oldKey);
        startRepeatKey(key);
        if (mIsAllowedDraggingFinger) {
            processDraggingFingerInToNewKey(key, x, y, eventTime);
        }
        // HACK: On some devices, quick successive touches may be reported as a sudden move by
        // touch panel firmware. This hack detects such cases and translates the move event to
        // successive up and down events.
        // TODO: Should find a way to balance gesture detection and this hack.
        else if (sNeedsPhantomSuddenMoveEventHack
                && getDistance(x, y, lastX, lastY) >= mPhantomSuddenMoveThreshold) {
            processPhantomSuddenMoveHack(key, x, y, eventTime, oldKey, lastX, lastY);
        }
        // HACK: On some devices, quick successive proximate touches may be reported as a bogus
        // down-move-up event by touch panel firmware. This hack detects such cases and breaks
        // these events into separate up and down events.
        else if (sNeedsProximateBogusDownMoveUpEventHack && sTimeRecorder.isInFastTyping(eventTime)
                && mBogusMoveEventDetector.isCloseToActualDownEvent(x, y)) {
            processProximateBogusDownMoveUpEventHack(key, x, y, eventTime, oldKey, lastX, lastY);
        }
        // HACK: If there are currently multiple touches, register the key even if the finger
        // slides off the key. This defends against noise from some touch panels when there are
        // close multiple touches.
        // Caveat: When in chording input mode with a modifier key, we don't use this hack.
        else if (getActivePointerTrackerCount() > 1
                && !sPointerTrackerQueue.hasModifierKeyOlderThan(this)) {
            if (DEBUG_MODE) {
                Log.w(TAG, String.format("[%d] onMoveEvent:"
                        + " detected sliding finger while multi touching", mPointerId));
            }
            onUpEvent(x, y, eventTime);
            cancelTrackingForAction();
            setReleasedKeyGraphics(oldKey);
        } else {
            if (!mIsDetectingGesture) {
                cancelTrackingForAction();
            }
            setReleasedKeyGraphics(oldKey);
        }
    }

    private void dragFingerOutFromOldKey(final Key oldKey, final int x, final int y) {
        // The pointer has been slid out from the previous key, we must call onRelease() to
        // notify that the previous key has been released.
        processDraggingFingerOutFromOldKey(oldKey);
        if (mIsAllowedDraggingFinger) {
            onMoveToNewKey(null, x, y);
        } else {
            if (!mIsDetectingGesture) {
                cancelTrackingForAction();
            }
        }
    }

    private void onMoveEventInternal(final int x, final int y, final long eventTime) {
        final int lastX = mLastX;
        final int lastY = mLastY;
        final Key oldKey = mCurrentKey;
        final Key newKey = onMoveKey(x, y);

        if (sShouldHandleGesture) {
            // Register move event on gesture tracker.
            onGestureMoveEvent(x, y, eventTime, true /* isMajorEvent */, newKey);
            if (sInGesture) {
                mCurrentKey = null;
                setReleasedKeyGraphics(oldKey);
                return;
            }
        }

        if (newKey != null) {
            if (oldKey != null && isMajorEnoughMoveToBeOnNewKey(x, y, eventTime, newKey)) {
                dragFingerFromOldKeyToNewKey(newKey, x, y, eventTime, oldKey, lastX, lastY);
            } else if (oldKey == null) {
                // The pointer has been slid in to the new key, but the finger was not on any keys.
                // In this case, we must call onPress() to notify that the new key is being pressed.
                processDraggingFingerInToNewKey(newKey, x, y, eventTime);
            }
        } else { // newKey == null
            if (oldKey != null && isMajorEnoughMoveToBeOnNewKey(x, y, eventTime, newKey)) {
                dragFingerOutFromOldKey(oldKey, x, y);
            }
        }
        if (mIsInSlidingKeyInput) {
            sDrawingProxy.showSlidingKeyInputPreview(this);
        }
    }

    private void onUpEvent(final int x, final int y, final long eventTime) {
        if (DEBUG_EVENT) {
            printTouchEvent("onUpEvent  :", x, y, eventTime);
        }

        sTimerProxy.cancelUpdateBatchInputTimer(this);
        if (!sInGesture) {
            if (mCurrentKey != null && mCurrentKey.isModifier()) {
                // Before processing an up event of modifier key, all pointers already being
                // tracked should be released.
                sPointerTrackerQueue.releaseAllPointersExcept(this, eventTime);
            } else {
                sPointerTrackerQueue.releaseAllPointersOlderThan(this, eventTime);
            }
        }
        onUpEventInternal(x, y, eventTime);
        sPointerTrackerQueue.remove(this);
    }

    // Let this pointer tracker know that one of newer-than-this pointer trackers got an up event.
    // This pointer tracker needs to keep the key top graphics "pressed", but needs to get a
    // "virtual" up event.
    @Override
    public void onPhantomUpEvent(final long eventTime) {
        if (DEBUG_EVENT) {
            printTouchEvent("onPhntEvent:", mLastX, mLastY, eventTime);
        }
        onUpEventInternal(mLastX, mLastY, eventTime);
        cancelTrackingForAction();
    }

    private void onUpEventInternal(final int x, final int y, final long eventTime) {
        sTimerProxy.cancelKeyTimersOf(this);
        final boolean isInDraggingFinger = mIsInDraggingFinger;
        final boolean isInSlidingKeyInput = mIsInSlidingKeyInput;
        resetKeySelectionByDraggingFinger();
        mIsDetectingGesture = false;
        final Key currentKey = mCurrentKey;
        mCurrentKey = null;
        final int currentRepeatingKeyCode = mCurrentRepeatingKeyCode;
        mCurrentRepeatingKeyCode = Constants.NOT_A_CODE;
        // Release the last pressed key.
        setReleasedKeyGraphics(currentKey);

        if (isShowingMoreKeysPanel()) {
            if (!mIsTrackingForActionDisabled) {
                final int translatedX = mMoreKeysPanel.translateX(x);
                final int translatedY = mMoreKeysPanel.translateY(y);
                mMoreKeysPanel.onUpEvent(translatedX, translatedY, mPointerId, eventTime);
            }
            mMoreKeysPanel.dismissMoreKeysPanel();
            mMoreKeysPanel = null;
            return;
        }

        if (sInGesture) {
            if (currentKey != null) {
                callListenerOnRelease(currentKey, currentKey.getCode(), true /* withSliding */);
            }
            mayEndBatchInput(eventTime);
            return;
        }

        if (mIsTrackingForActionDisabled) {
            return;
        }
        if (currentKey != null && currentKey.isRepeatable()
                && (currentKey.getCode() == currentRepeatingKeyCode) && !isInDraggingFinger) {
            return;
        }
        detectAndSendKey(currentKey, mKeyX, mKeyY, eventTime);
        if (isInSlidingKeyInput) {
            callListenerOnFinishSlidingInput();
        }
    }

    public void onShowMoreKeysPanel(final MoreKeysPanel panel) {
        setReleasedKeyGraphics(mCurrentKey);
        final int translatedX = panel.translateX(mLastX);
        final int translatedY = panel.translateY(mLastY);
        panel.onDownEvent(translatedX, translatedY, mPointerId, SystemClock.uptimeMillis());
        mMoreKeysPanel = panel;
    }

    @Override
    public void cancelTrackingForAction() {
        if (isShowingMoreKeysPanel()) {
            return;
        }
        mIsTrackingForActionDisabled = true;
    }

    public void onLongPressed() {
        resetKeySelectionByDraggingFinger();
        cancelTrackingForAction();
        setReleasedKeyGraphics(mCurrentKey);
        sPointerTrackerQueue.remove(this);
    }

    private void onCancelEvent(final int x, final int y, final long eventTime) {
        if (DEBUG_EVENT) {
            printTouchEvent("onCancelEvt:", x, y, eventTime);
        }

        cancelBatchInput();
        cancelAllPointerTrackers();
        sPointerTrackerQueue.releaseAllPointers(eventTime);
        onCancelEventInternal();
    }

    private void onCancelEventInternal() {
        sTimerProxy.cancelKeyTimersOf(this);
        setReleasedKeyGraphics(mCurrentKey);
        resetKeySelectionByDraggingFinger();
        if (isShowingMoreKeysPanel()) {
            mMoreKeysPanel.dismissMoreKeysPanel();
            mMoreKeysPanel = null;
        }
    }

    private boolean isMajorEnoughMoveToBeOnNewKey(final int x, final int y, final long eventTime,
            final Key newKey) {
        if (mKeyDetector == null) {
            throw new NullPointerException("keyboard and/or key detector not set");
        }
        final Key curKey = mCurrentKey;
        if (newKey == curKey) {
            return false;
        }
        if (curKey == null /* && newKey != null */) {
            return true;
        }
        // Here curKey points to the different key from newKey.
        final int keyHysteresisDistanceSquared = mKeyDetector.getKeyHysteresisDistanceSquared(
                mIsInSlidingKeyInput);
        final int distanceFromKeyEdgeSquared = curKey.squaredDistanceToEdge(x, y);
        if (distanceFromKeyEdgeSquared >= keyHysteresisDistanceSquared) {
            if (DEBUG_MODE) {
                final float distanceToEdgeRatio = (float)Math.sqrt(distanceFromKeyEdgeSquared)
                        / mKeyboard.mMostCommonKeyWidth;
                Log.d(TAG, String.format("[%d] isMajorEnoughMoveToBeOnNewKey:"
                        +" %.2f key width from key edge", mPointerId, distanceToEdgeRatio));
            }
            return true;
        }
        if (sNeedsProximateBogusDownMoveUpEventHack && !mIsAllowedDraggingFinger
                && sTimeRecorder.isInFastTyping(eventTime)
                && mBogusMoveEventDetector.hasTraveledLongDistance(x, y)) {
            if (DEBUG_MODE) {
                final float keyDiagonal = (float)Math.hypot(
                        mKeyboard.mMostCommonKeyWidth, mKeyboard.mMostCommonKeyHeight);
                final float lengthFromDownRatio =
                        mBogusMoveEventDetector.mAccumulatedDistanceFromDownKey / keyDiagonal;
                Log.d(TAG, String.format("[%d] isMajorEnoughMoveToBeOnNewKey:"
                        + " %.2f key diagonal from virtual down point",
                        mPointerId, lengthFromDownRatio));
            }
            return true;
        }
        return false;
    }

    private void startLongPressTimer(final Key key) {
        if (sInGesture) return;
        if (key == null) return;
        if (!key.isLongPressEnabled()) return;
        // Caveat: Please note that isLongPressEnabled() can be true even if the current key
        // doesn't have its more keys. (e.g. spacebar, globe key) If we are in the dragging finger
        // mode, we will disable long press timer of such key.
        // We always need to start the long press timer if the key has its more keys regardless of
        // whether or not we are in the dragging finger mode.
        if (mIsInDraggingFinger && key.getMoreKeys() == null) return;

        final int delay = getLongPressTimeout(key.getCode());
        sTimerProxy.startLongPressTimerOf(this, delay);
    }

    private int getLongPressTimeout(final int code) {
        if (code == Constants.CODE_SHIFT) {
            return sParams.mLongPressShiftLockTimeout;
        }
        final int longpressTimeout = Settings.getInstance().getCurrent().mKeyLongpressTimeout;
        if (mIsInSlidingKeyInput) {
            // We use longer timeout for sliding finger input started from the modifier key.
            return longpressTimeout * MULTIPLIER_FOR_LONG_PRESS_TIMEOUT_IN_SLIDING_INPUT;
        }
        return longpressTimeout;
    }

    private void detectAndSendKey(final Key key, final int x, final int y, final long eventTime) {
        if (key == null) {
            callListenerOnCancelInput();
            return;
        }

        final int code = key.getCode();
        callListenerOnCodeInput(key, code, x, y, eventTime);
        callListenerOnRelease(key, code, false /* withSliding */);
    }

    private void startRepeatKey(final Key key) {
        if (sInGesture) return;
        if (key == null) return;
        if (!key.isRepeatable()) return;
        // Don't start key repeat when we are in the dragging finger mode.
        if (mIsInDraggingFinger) return;
        final int startRepeatCount = 1;
        startKeyRepeatTimer(startRepeatCount);
    }

    public void onKeyRepeat(final int code, final int repeatCount) {
        final Key key = getKey();
        if (key == null || key.getCode() != code) {
            mCurrentRepeatingKeyCode = Constants.NOT_A_CODE;
            return;
        }
        mCurrentRepeatingKeyCode = code;
        mIsDetectingGesture = false;
        final int nextRepeatCount = repeatCount + 1;
        startKeyRepeatTimer(nextRepeatCount);
        callListenerOnPressAndCheckKeyboardLayoutChange(key, repeatCount);
        callListenerOnCodeInput(key, code, mKeyX, mKeyY, SystemClock.uptimeMillis());
    }

    private void startKeyRepeatTimer(final int repeatCount) {
        final int delay =
                (repeatCount == 1) ? sParams.mKeyRepeatStartTimeout : sParams.mKeyRepeatInterval;
        sTimerProxy.startKeyRepeatTimerOf(this, repeatCount, delay);
    }

    private void printTouchEvent(final String title, final int x, final int y,
            final long eventTime) {
        final Key key = mKeyDetector.detectHitKey(x, y);
        final String code = KeyDetector.printableCode(key);
        Log.d(TAG, String.format("[%d]%s%s %4d %4d %5d %s", mPointerId,
                (mIsTrackingForActionDisabled ? "-" : " "), title, x, y, eventTime, code));
    }
}
