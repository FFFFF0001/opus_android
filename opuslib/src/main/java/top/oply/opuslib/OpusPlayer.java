package top.oply.opuslib;

import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.util.Log;

import java.nio.ByteBuffer;

/**
 * Created by young on 2015/7/2.
 */
public class OpusPlayer {

    private OpusPlayer(){
    }
    private static volatile OpusPlayer singleton ;
    public static OpusPlayer getInstance(){
        if(singleton==null)
            synchronized(OpusPlayer.class){
                if(singleton==null)
                    singleton = new OpusPlayer();
            }
        return singleton;
    }

    private OpusTool opusLib = new OpusTool();
    private static final String TAG = OpusPlayer.class.getName();
    private static final int STATE_NONE = 0;
    private static final int STATE_STARTED = 1;
    private static final int STATE_PAUSED = 2;

    private volatile int state = STATE_NONE;
    private AudioTrack audioTrack;
    private int bufferSize;

    private long lastNoficationTime = 0;

    private String currentFileName;

    private volatile Thread playTread = new Thread();
    private OpusEvent mEventSender = null;

    public void setEventSender(OpusEvent es) {
        mEventSender = es;
    }

    class PlayThread implements Runnable {
        public void run() {
            readAudioDataFromFile();
        }
    }

    public void play(String fileName) {
        //if already playing, stop current playback
        if (state != STATE_NONE) {
            stop();
        }
        state = STATE_NONE;
        currentFileName = fileName;

        if(!Utils.isFileExist(currentFileName) || opusLib.isOpusFile(currentFileName) == 0) {
            Log.e(TAG, "File does not exist, or it is not an opus file!");
            if(mEventSender != null)
                mEventSender.sendEvent(OpusEvent.PLAYING_FAILED);
            return;
        }

        int res = opusLib.openOpusFile(currentFileName);
        if (res == 0) {
            Log.e(TAG, "Open opus file error!");
            if(mEventSender != null)
                mEventSender.sendEvent(OpusEvent.PLAYING_FAILED);
            return;
        }

        try {
            bufferSize = AudioTrack.getMinBufferSize(48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
            audioTrack = new AudioTrack(AudioManager.STREAM_MUSIC, 48000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize, AudioTrack.MODE_STREAM);
            audioTrack.play();
        } catch (Exception e) {
            Utils.printE(TAG, e);
            destroyPlayer();
            return;
        }

        state = STATE_STARTED;
        if(mEventSender != null)
            mEventSender.sendEvent(OpusEvent.PLAYING_STARTED);
        playTread = new Thread( new PlayThread(),"OpusPlay Thrd");
        playTread.start();
    }

    protected void readAudioDataFromFile() {
        if (state != STATE_STARTED) {
            return;
        }
        ByteBuffer buffer = ByteBuffer.allocateDirect(bufferSize);
        boolean isFinished = false;
        while (state != STATE_NONE) {
            if (state == STATE_PAUSED){
                try {
                    Thread.sleep(100);
                    continue;
                }
                catch (Exception e) {
                    Log.e(TAG, e.toString());
                    continue;
                }

            }
            else  if (state == STATE_STARTED) {
                opusLib.readOpusFile(buffer, bufferSize);
                int size = opusLib.getSize();

                if (size != 0) {
                    buffer.rewind();
                    byte[] data = new byte[size];
                    buffer.get(data);
                    audioTrack.write(data, 0, size);
                }

                notifyProgress();
                isFinished = opusLib.getFinished() != 0;
                if (isFinished) {
                    break;
                }
            }
        }
        if (state != STATE_NONE)
            state = STATE_NONE;
        if(mEventSender != null)
            mEventSender.sendEvent(OpusEvent.PLAYING_FINISHED);
    }

    public void pause() {
        if (state == STATE_STARTED) {
            audioTrack.pause();
            state = STATE_PAUSED;
            if(mEventSender != null)
                mEventSender.sendEvent(OpusEvent.PLAYING_PAUSED);
        }
        notifyProgress();
    }

    public void resume() {
        if (state == STATE_PAUSED) {
            audioTrack.play();
            state = STATE_STARTED;
            if(mEventSender != null)
                mEventSender.sendEvent(OpusEvent.PLAYING_STARTED);
        }
    }

    public void stop() {
        state = STATE_NONE;
        try {
            Thread.sleep(200);
        }
        catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        destroyPlayer();
    }

    public String toggle(String fileName) {
        if (state == STATE_PAUSED) {
            resume();
            return "Pause";
        } else if (state == STATE_STARTED) {
            pause();
            return "Resume";
        } else {
            play(fileName);
            return "Pause";
        }
    }


    /**
     * Get duration, whose unit is second
     * @return duration
     */
    public long getDuration() {
        return opusLib.getTotalDuration();
    }

    /**
     * Get Position of current palyback, whose unit is second
     * @return duration
     */
    public long getPosition() {
        return opusLib.getCurrentPosition();
    }

    public void seekOpusFile(float scale) {
        opusLib.seekOpusFile(scale);
    }

    private void notifyProgress() {

        float scale = 0;
        //notify every 1 second
        if(System.currentTimeMillis() - lastNoficationTime >= 1000) {
            if(mEventSender != null)
                mEventSender.sendProgressEvent(getPosition(), getDuration());
        }
    }

    private void destroyPlayer() {
        opusLib.closeOpusFile();
        if (audioTrack != null ) {
            audioTrack.pause();
            audioTrack.flush();
            audioTrack.release();
            audioTrack = null;
        }
    }

    public boolean isWorking() {
        return state != STATE_NONE;
    }
    public void release() {
        if(state != STATE_NONE)
            stop();
    }
}
