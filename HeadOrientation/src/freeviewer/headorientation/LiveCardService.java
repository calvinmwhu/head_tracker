package freeviewer.headorientation;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import freeviewer.headorientation.MenuActivity;
import com.google.android.glass.timeline.LiveCard;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

public class LiveCardService extends Service {

	private LiveCard liveCard = null;
	private RemoteViews remoteView;
	private static boolean isSending = false;
	private DataSender sender = new DataSender();

	// sensor here
	private SensorManager sensorManager;
	private Sensor magnetometer;
	private Sensor rotationSensor;
	// update timer
	private Timer timer = null;

	float[] mRotationMatrix = new float[16];
	float[] mOrientation = new float[9];
	ArrayList<Float> fl = new ArrayList<Float>();

	private static float azimuth;
	private float startAngle = 0.0f;
	private float offset = 0.0f;
	private boolean shouldCalibrate = false;

	/**
	 * Class used for the client Binder. Because we know this service always
	 * runs in the same process as its clients, we don't need to deal with IPC.
	 */
	public class LocalBinder extends Binder {
		public void sendData() {
			if (!isSending) {
				if (!calibrationComplete()) {
					String str = "Please calibrate your glass before sending data";
					Toast t = Toast.makeText(LiveCardService.this, str,
							Toast.LENGTH_LONG);
					t.show();
				} else {
					Log.d("HEAD", "ready to send data");
					sender.start();
					isSending = true;
				}
			} else {
				String str = "another thread is already sending data";
				Toast toast = Toast.makeText(LiveCardService.this, str,
						Toast.LENGTH_SHORT);
				toast.show();
				Log.d("HEAD", str);
			}
		}

		public void stopSending() {
			if (isSending) {
				Log.d("HEAD", "ready to stop sending data");
				sender.interrupt();
				sender.quit();
				isSending = false;
			} else {
				String str = "the sending is already terminated or not started yet";
				Log.d("HEAD", str);
			}
		}

		public void calibrate() {
			shouldCalibrate = true;
		}
	}

	private final LocalBinder mBinder = new LocalBinder();

	@Override
	public IBinder onBind(Intent arg0) {
		// TODO Auto-generated method stub
		return mBinder;
	}

	@Override
	public void onDestroy() {
		// stop timer. this is only used for the timer demo
		if (timer != null) {
			timer.cancel();
			timer.purge();
			timer = null;
		}
		if (rotationSensor != null)
			sensorManager.unregisterListener(listener, rotationSensor);
		if (magnetometer != null)
			sensorManager.unregisterListener(listener, magnetometer);

		if (liveCard != null) {
			if (liveCard.isPublished())
				liveCard.unpublish();
			liveCard = null;
		}
		Log.d("HEAD", "LiveCardService destroyed");
		azimuth = 0.0f;
		super.onDestroy();
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// TODO Auto-generated method stub
		Log.d("OnStartCommand", "service to start");
		if (liveCard == null) {
			// create one
			liveCard = new LiveCard(getApplicationContext(), "myLiveCard");
			// setup liveCard view
			remoteView = new RemoteViews(this.getPackageName(),
					R.layout.orientation);
			liveCard.setViews(remoteView);

			// setup menu items
			Log.d("HEAD", "ready to start an activity");
			Intent menuIntent = new Intent(this, MenuActivity.class);
			menuIntent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP
					| Intent.FLAG_ACTIVITY_CLEAR_TASK);
			liveCard.setAction(PendingIntent.getActivity(this, 100, menuIntent,
					0));

			// publish card
			liveCard.publish(LiveCard.PublishMode.REVEAL); // LiveCard.PublishMode.SILENT
			Log.d("HEAD", "live card published");

			// setup sensor manager
			sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
			List<Sensor> deviceSensors = sensorManager
					.getSensorList(Sensor.TYPE_ALL);
			for (Iterator<Sensor> iterator = deviceSensors.iterator(); iterator
					.hasNext();) {
				Sensor sensor = (Sensor) iterator.next();
				Log.d("sensor avaliable ", sensor.getName());
			}

			rotationSensor = sensorManager
					.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

			magnetometer = sensorManager
					.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
			if (rotationSensor != null
					&& sensorManager.registerListener(listener, rotationSensor,
							SensorManager.SENSOR_DELAY_FASTEST)) {
				Log.d("On Start", "rotation sensor enabled");
			} else {
				Log.d("On Start", "cannot obtain rotation sensor!");
			}
			if (magnetometer != null
					&& sensorManager.registerListener(listener, magnetometer,
							SensorManager.SENSOR_DELAY_NORMAL)) {
				Log.d("On Start", "magnetometer sensor enabled");
			} else {
				Log.d("On Start", "cannot obtain magnetometer sensor!");
			}

			// setup the timer:

			if (timer == null) {
				timer = new Timer();
				timer.schedule(new TimerTask() {
					@Override
					public void run() {
						render();
					}
				}, 1000, 200); // start after 1 second, and repeat every second
			}

		} else {
			// ignore the card
		}
		return Service.START_STICKY;
	}

	// listener for the sensor
	SensorEventListener listener = new SensorEventListener() {
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
			Log.d("Sensor Event", "Accuracy changed!");
		}

		@Override
		public void onSensorChanged(SensorEvent event) {
			// Log.d("Sensor Event", "New data received");
			if (event.sensor.getType() == Sensor.TYPE_ROTATION_VECTOR) {
				SensorManager.getRotationMatrixFromVector(mRotationMatrix,
						event.values);
				SensorManager.remapCoordinateSystem(mRotationMatrix,
						SensorManager.AXIS_X, SensorManager.AXIS_Z,
						mRotationMatrix);
				SensorManager.getOrientation(mRotationMatrix, mOrientation);
				azimuth = mOrientation[0] - startAngle;
				if (shouldCalibrate) {
					fl.add(Float.valueOf(azimuth));
					Log.d("HEAD", String.valueOf(azimuth));
					if (fl.size() == 20) {
						float sum = 0.0f;
						for (Float x : fl) {
							sum += x.floatValue();
						}
						startAngle = sum / fl.size();
						String finish = "Calibration completed! Initial Angle: "
								+ (float) Math.toDegrees(startAngle);
						Toast t = Toast.makeText(LiveCardService.this, finish,
								Toast.LENGTH_LONG);
						t.show();
						shouldCalibrate = false;
					}
				}else if(calibrationComplete()){
					azimuth = Math.abs(azimuth);
				}else{
					
				}
			}
		}
	};

	public static float getAzimuth() {
		return azimuth;
	}

	public boolean calibrationComplete() {
		return fl.size() != 0;
	}

	// update the textview for the sensor value
	void render() {
		remoteView.setTextViewText(R.id.angle,
				String.valueOf((float) Math.toDegrees(azimuth)));
		if (liveCard != null)
			liveCard.setViews(remoteView);

	}

}
