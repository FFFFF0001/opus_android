package top.oply.opuslib;

import android.os.Environment;

import java.io.File;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by young on 2015/8/7.
 */
public class OpusTrackInfo {

    private static volatile OpusTrackInfo singleton ;
    public static OpusTrackInfo getInstance(){
        if(singleton==null)
            synchronized(OpusTrackInfo.class){
                if(singleton==null)
                    singleton = new OpusTrackInfo();
            }
        return singleton;
    }

    private String TAG = OpusTrackInfo.class.getName();
    private OpusEvent mEventSender;
    private OpusTool mTool = new OpusTool();
    private String appExtDir;
    private File requestDirFile;
    private Thread mThread = new Thread();
    private AudioPlayList mTrackInforList = new AudioPlayList();
    private Utils.AudioTime mAudioTime = new Utils.AudioTime();

    public static final String TITLE_TITLE = "TITLE";
    public static final String TITLE_ABS_PATH = "ABS_PATH";
    public static final String TITLE_DURATION = "DURATION";
    public static final String TITLE_IMG = "TITLE_IMG";

    public void setEvenSender(OpusEvent opusEven) {
        mEventSender = opusEven;
    }
    private OpusTrackInfo() {

        //create OPlayer directory if it does not exist.
        if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))
            return;
        String sdcardPath = Environment.getExternalStorageDirectory().getAbsolutePath();
        appExtDir = sdcardPath + "/OPlayer/";
        File fp = new File(appExtDir);
        if(!fp.exists())
            fp.mkdir();

        getTrackInfor(appExtDir);
    }

    public void addOpusFile(String file) {
        File f = new File(file);
        if(f.exists() && "opus".equalsIgnoreCase(Utils.getExtention(file))
                && mTool.openOpusFile(file) != 0) {
            Map<String, Object> map = new HashMap<String, Object>();
            map.put(TITLE_TITLE, f.getName());
            map.put(TITLE_ABS_PATH, file);
            mAudioTime.setTimeInSecond(mTool.getTotalDuration());
            map.put(TITLE_DURATION, mAudioTime.getTime());
            //TODO: get imagin from opus files
            map.put(TITLE_IMG, 0);
            mTrackInforList.add(map);
            mTool.closeOpusFile();

            mEventSender.sendTrackinforEvent(mTrackInforList);
        }
    }

    public String getAppExtDir() {
        return  appExtDir;
    }

    public void sendTrackInforToUi() {
        mEventSender.sendTrackinforEvent(mTrackInforList);
    }
    public AudioPlayList getTrackInfor() {
        mEventSender.sendTrackinforEvent(mTrackInforList);
        return mTrackInforList;
    }

    private void getTrackInfor(String Dir) {
        if(Dir.length() == 0)
            Dir = appExtDir;
        File file = new File(Dir);
        if (file.exists() && file.isDirectory())
            requestDirFile = file;

        mThread = new Thread(new MyThread(), "Opus Trc Trd");
        mThread.start();
    }
    private void prepareTrackInfor(File file) {
        File[] files = file.listFiles();
        for(File f : files) {
            if (f.isFile()) {
                String name = f.getName();
                String absPath = f.getAbsolutePath();
                if ("opus".equalsIgnoreCase(Utils.getExtention(name))
                        && mTool.openOpusFile(absPath) != 0) {
                    Map<String, Object> map = new HashMap<String, Object>();
                    map.put(TITLE_TITLE, f.getName());
                    map.put(TITLE_ABS_PATH,absPath);
                    mAudioTime.setTimeInSecond(mTool.getTotalDuration());
                    map.put(TITLE_DURATION, mAudioTime.getTime());
                    //TODO: get imagin from opus files
                    map.put(TITLE_IMG, 0);
                    mTrackInforList.add(map);
                    mTool.closeOpusFile();
                }

            } else if (f.isDirectory()){
                prepareTrackInfor(f);
            }
        }
    }

    public static class AudioPlayList implements Serializable {
        public AudioPlayList() {

        }
        public static final long serialVersionUID=1234567890987654321L;
        private List<Map<String, Object>> mAudioInforList = new ArrayList<Map<String, Object>>();

        public void add(Map<String, Object> map) {
            mAudioInforList.add(map);
        }
        public List<Map<String, Object>> getList() {
            return mAudioInforList;
        }
    }

    class MyThread implements Runnable {
        public void run() {
            prepareTrackInfor(requestDirFile);
            mEventSender.sendTrackinforEvent(mTrackInforList);
        }
    }

    public void release() {
        try{
            if(mThread.isAlive())
                mThread.interrupt();
        }catch (Exception e) {
            Utils.printE(TAG, e);
        }
    }
}
