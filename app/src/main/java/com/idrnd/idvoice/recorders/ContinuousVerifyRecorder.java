package com.idrnd.idvoice.recorders;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import com.idrnd.idvoice.models.AudioRecord;
import com.idrnd.idvoice.ui.dialogs.interfaces.OnNextVerifyResultListener;
import com.idrnd.idvoice.utils.Prefs;
import com.idrnd.idvoice.utils.verification.EngineManager;
import com.idrnd.idvoice.utils.verification.VoiceVerifyEngine;

import net.idrnd.voicesdk.common.VoiceSdkEngineException;
import net.idrnd.voicesdk.core.common.VoiceTemplate;
import net.idrnd.voicesdk.verify.VoiceVerifyStream;

import static com.idrnd.idvoice.utils.Prefs.VoiceTemplateType.TextIndependent;

/**
 * Class for audio recording combined with continuous voice verification. It does not employ anti-spoofing check.
 */
public class ContinuousVerifyRecorder extends AudioRecorder {

    private static final String TAG = ContinuousVerifyRecorder.class.getSimpleName();

    // For a detailed explanation of this parameter please refer to https://docs.idrnd.net/voice/#idvoice-speaker-verification
    // ('Continuous speaker verification' section)
    private static final int WINDOW_LENGTH_IN_SECONDS = 4;

    private VoiceVerifyStream voiceVerifyStream;
    private VoiceVerifyEngine verifyEngine;
    private OnNextVerifyResultListener onNextVerifyResultListener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    /**
     * Class for audio recording combined with continuous voice verification. It does not employ anti-spoofing check
     * @param recorderSampleRate audio record sampling rate
     * @param onNextVerifyResultListener callback for processing produced verify results
     */
    public ContinuousVerifyRecorder(int recorderSampleRate, OnNextVerifyResultListener onNextVerifyResultListener) {
        super(recorderSampleRate);
        this.onNextVerifyResultListener = onNextVerifyResultListener;

        // Get text independent voice template
        VoiceTemplate enrollTemplate = VoiceTemplate.deserialize(
                Prefs.getInstance().getVoiceTemplate(TextIndependent)
        );

        // It is important to keep this object alive the whole time VoiceVerifyStream exists
        // For continuous verification, text-independent verification should be used
        verifyEngine = EngineManager.getInstance().getVerifyEngine(TextIndependent);

        voiceVerifyStream = verifyEngine.getVoiceVerifyStream(
                enrollTemplate,
                recorderSampleRate,
                WINDOW_LENGTH_IN_SECONDS
        );
    }

    public void setOnNextVerifyResultListener(OnNextVerifyResultListener onNextVerifyResultListener) {
        this.onNextVerifyResultListener = onNextVerifyResultListener;
    }

    @Override
    public synchronized AudioRecord stopRecording() {
        voiceVerifyStream.reset();
        return super.stopRecording();
    }

    @Override
    protected void onNextAudioChunk(byte[] audioChunk) throws VoiceSdkEngineException {
        super.onNextAudioChunk(audioChunk);
        try {
            // Process new byte array with audio chunk
            voiceVerifyStream.addSamples(audioChunk);

            // Check the produced verify result
            if (voiceVerifyStream.hasVerifyResults()) {
                // Return verify probability
                float newProbability = voiceVerifyStream.getVerifyResult().getVerifyResult().getProbability();

                handler.post(() -> onNextVerifyResultListener.onNextVerifyResult(newProbability));
            }
        } catch (VoiceSdkEngineException e) {
            Log.e(TAG, "Error while audio chunk processing with use VoiceVerifyStream", e);
            throw e;
        }
    }
}







