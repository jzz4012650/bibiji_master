package com.bibizhaoji.pocketsphinx;

import java.lang.ref.WeakReference;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.bibizhaoji.bibiji.G;
import com.bibizhaoji.bibiji.LockScreenActivity;
import com.bibizhaoji.bibiji.MainActivity;
import com.bibizhaoji.bibiji.aidl.IPPClient;
import com.bibizhaoji.bibiji.aidl.IWorkerService;
import com.bibizhaoji.bibiji.utils.Pref;

public class WorkerRemoteRecognizerService extends Service implements RecognitionListener {
	static {
		System.loadLibrary("pocketsphinx_jni");
	}

	public static final String ACTION = "com.bibizhaoji.action.CONNECT_TO_SERVICE";
	private RecognizerTask recTask;
	private Thread recThread;
	private boolean listening;
	private long tStart;
	private long tEnd;
	Context mContext;
	// 识别不出来或为空
	public static int STATE_NONE = 0;
	// 命中分词
	public static int STATE_MARK = 1;
	// 识别出关键词
	public static int STATE_MATCH = 2;

	public IPPClient mClient;

	@Override
	public void onCreate() {
		// TODO Auto-generated method stub
		super.onCreate();
		Log.d(G.LOG_TAG, "RemoteRecognizerService created...");
		mContext = this;
		Log.d(G.LOG_TAG, "isNoDisturbingModeOnlyNight------>" + Pref.isNightModeOn());

		recTask = new RecognizerTask(mContext);
		recThread = new Thread(this.recTask);
		recTask.setRecognitionListener(this);
		recThread.start();
	}

	@Override
	public IBinder onBind(Intent arg0) {
		// 返回远程服务的实例
		Log.d(G.LOG_TAG, "RemoteRecognizerService bound...");

		return MyBinder.getInstance(this);
	}

	@Override
	public boolean onUnbind(Intent intent) {
		Log.d(G.LOG_TAG, "RemoteRecognizerService unbound...");

		return super.onUnbind(intent);
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Log.d(G.LOG_TAG, "RemoteRecognizerService start...");
		tStart = System.currentTimeMillis();
		recTask.start();
		return super.onStartCommand(intent, flags, startId);

	}

	static class MyBinder extends IWorkerService.Stub {

		private static volatile MyBinder sInstance;
		private WeakReference<WorkerRemoteRecognizerService> mReference;

		public MyBinder(WorkerRemoteRecognizerService blockCentralService) {
			mReference = new WeakReference<WorkerRemoteRecognizerService>(blockCentralService);
		}

		private static MyBinder getInstance(WorkerRemoteRecognizerService workerService) {
			if (sInstance == null) {
				synchronized (MyBinder.class) {
					if (sInstance == null) {
						sInstance = new MyBinder(workerService);
					}
				}
			}
			return sInstance;
		}

		private static void destroy() {
			if (sInstance != null) {
				if (sInstance.mReference != null) {
					sInstance.mReference.clear();
					sInstance.mReference = null;
				}
				sInstance = null;
			}
		}

		@Override
		public boolean register(IPPClient client) throws RemoteException {
			mReference.get().mClient = client;
			return true;
		}

		@Override
		public boolean start() throws RemoteException {
			mReference.get().recTask.start();
			mReference.get().listening = true;
			Log.d(G.LOG_TAG, "worker recTask.start()");
			return true;
		}

		@Override
		public boolean stop() throws RemoteException {
			mReference.get().recTask.stop();
			Log.d(G.LOG_TAG, "worker recTask.stop()");
			mReference.get().mClient.onResult(STATE_NONE);
			mReference.get().listening = false;
			return true;
		}

		@Override
		public boolean shutdown() throws RemoteException {
			mReference.get().recTask.shutdown();
			Log.d(G.LOG_TAG, "worker recTask.shutdown()");

			return true;
		}

	}

	@Override
	public void onPartialResults(Bundle b) {
		if (listening) {
			int result = STATE_NONE;
			final String hyp = b.getString("hyp");
			if (hyp != null) {
				String data[] = hyp.split(" ");
				for (String string : data) {
					if (result == STATE_MARK) {
						if (string.contains(G.REC_WORD1) || string.contains(G.REC_WORD2)) {
							result = STATE_MATCH;
							jumpToActivity();
						}
					} else if (result == STATE_NONE) {
						if (string.contains(G.REC_WORD1) || string.contains(G.REC_WORD2)) {
							result = STATE_MARK;
						}
					}

				}
			}
			Log.d(G.LOG_TAG_RECWORD, "*********get rec_word:" + hyp);
			try {
				mClient.onResult(result);
			} catch (RemoteException e) {
				e.printStackTrace();
			}
		}

	}

	private void jumpToActivity() {
		if (G.isMainActivityRunning) {
			Intent intent = new Intent(this, MainActivity.class);
			intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(intent);
		} else {
			Intent i = new Intent(this, LockScreenActivity.class);
			i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(i);
		}
		recTask.stop();
	}

	@Override
	public void onResults(Bundle b) {
		final String hyp = b.getString("hyp");
		Log.d(G.LOG_TAG, "|||||||||||recognizition finished:" + hyp);
	}

	@Override
	public void onError(int err) {
		Log.d(G.LOG_TAG, "PocketSphinx got an error.");
	}

	@Override
	public void onDestroy() {
		tEnd = System.currentTimeMillis();
		float tDuration = (tEnd - tStart) / 1000;
		Log.d(G.LOG_TAG, String.format("time duration: %.2fs", tDuration));
		recTask.stop();
		super.onDestroy();
	}

}
