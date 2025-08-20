package com.generalplus.ffmpegLib;

import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class ffmpegWrapper implements GLSurfaceView.Renderer {

    private final static String TAG = "ffmpegWrapper";
    private static ffmpegWrapper m_Instance;
    private static Handler m_NowViewHandler;

    public final static String LOW_LOADING_TRANSCODE_OPTIONS = "qmin=15;qmax=35;b=400000;g=15;bf=0;refs=2;weightp=simple;level=2.2;" +
            "x24-params=lookahead-threads=3:subme=4:chroma_qp_offset=0";

    public final static int FFMPEG_STATUS_PLAYING                    = 0x00;
    public final static int FFMPEG_STATUS_STOPPED                    = 0x01;
    public final static int FFMPEG_STATUS_SAVESNAPSHOTCOMPLETE      = 0x02;
    public final static int FFMPEG_STATUS_SAVEVIDEOCOMPLETE         = 0x03;
    public final static int FFMPEG_STATUS_BUFFERING                 = 0x04;

    public final static int EXTRACTOR_OK                            = 0;
    public final static int EXTRACTOR_BUSY                          = 1;
    public final static int EXTRACTOR_READFILEFAILED                = 2;
    public final static int EXTRACTOR_DECODEFAILED                  = 3;
    public final static int EXTRACTOR_NOSUCHFRAME                   = 4;

    public final static int CODEC_ID_NONE                          = 0;
    public final static int CODEC_ID_MJPEG                         = 8;
    public final static int CODEC_ID_H264                          = 28;

    public ffmpegWrapper() {
        m_Instance = this;
        Log.d(TAG, "ffmpegWrapper instanciado com sucesso");
    }

    public static ffmpegWrapper getInstance() {
        return m_Instance;
    }

    @Override
    public void onSurfaceCreated(GL10 unused, EGLConfig config) {
        Log.d(TAG, "onSurfaceCreated ... ");
        naInitDrawFrame();
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        naDrawFrame();
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        Log.d(TAG, "onSurfaceChanged ... width=" + width + ", height=" + height);
        naSetup(width, height);
    }

    public void SetViewHandler(Handler ViewHandler) {
        m_NowViewHandler = ViewHandler;
    }

    void StatusChange(int i32Status) {
        if (m_NowViewHandler != null) {
            Message msg = new Message();
            msg.what = i32Status;
            m_NowViewHandler.sendMessage(msg);
        }
    }

    public static native int naInitAndPlay(String pFileName, String pOptions);
    public static native String naGetVideoInfo(String pFileName);
    public static native int[] naGetVideoRes();
    public static native int naSetup(int pWidth, int pHeight);
    public static native int naPlay();
    public static native int naStop();
    public static native int naPause();
    public static native int naResume();
    public static native int naSeek(long lPos);
    public static native int naSetStreaming(boolean bEnable);
    public static native int naSetEncodeByLocalTime(boolean bEnable);
    public static native int naSetDebugMessage(boolean bRepeat);
    public static native int naSetRepeat(boolean bRepeat);
    public static native int naSetForceToTranscode(boolean bEnable);
    public static native long naGetDuration();
    public static native long naGetPosition();
    public static native int naInitDrawFrame();
    public static native int naDrawFrame();
    public static native int naStatus();
    public static native long naGetRevSizeCnt();
    public static native long naGetFrameCnt();
    public static native int naGetStreamCodecID();
    public static native int naSaveSnapshot(String pFileName);
    public static native int naSaveVideo(String pFileName);
    public static native int naStopSaveVideo();
    public static native int naExtractFrame(String VideoPath, String SavePath, long frameIdx);
    public static native int naSetTransCodeOptions(String pOption);
    public static native int naSetDecodeOptions(String pOption);
    public static native int naSetScaleMode(int i32Mode);
    public static native int naSetCovertDecodeFrameFormat(int i32Mode);
    public static native int naSetBufferingTime(long bufferTime);
    public static native ffDecodeFrame naGetDecodeFrame();
    public static native int naSetZoomInRatio(float fRatio);
}