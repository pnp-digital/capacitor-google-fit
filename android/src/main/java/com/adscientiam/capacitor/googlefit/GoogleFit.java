package com.adscientiam.capacitor.googlefit;

import android.content.Intent;

import android.support.annotation.NonNull;

import com.getcapacitor.JSObject;
import com.getcapacitor.NativePlugin;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.google.android.gms.auth.api.signin.GoogleSignIn;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.Bucket;
import com.google.android.gms.fitness.data.DataPoint;
import com.google.android.gms.fitness.data.DataSet;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.data.Field;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@NativePlugin(
  requestCodes = {
    GoogleFit.GOOGLE_FIT_PERMISSIONS_REQUEST_CODE
  }
)
public class GoogleFit extends Plugin {

  public static final String TAG = "HistoryApi";
  static final int GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 19849;
  private int steps, calories, todayStep, todayCal;
  private float weight, height, distances, todayDist;

  public GoogleFit() { }

  private FitnessOptions getFitnessSignInOptions() {
    // FitnessOptions instance, declaring the Fit API data types
    // and access required
    return FitnessOptions.builder()
      .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
      .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
      .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
      .addDataType(DataType.TYPE_ACTIVITY_SEGMENT, FitnessOptions.ACCESS_WRITE)
      .addDataType(DataType.TYPE_SPEED, FitnessOptions.ACCESS_WRITE)
      .build();
  }

  private GoogleSignInAccount getAccount() {
    return GoogleSignIn.getLastSignedInAccount(getActivity());
  }

  @PluginMethod()
  public void connectToGoogleFit(PluginCall call) {
    // Check if the user has permissions to talk to Fitness APIs,
    // otherwise authenticate the user and request required permissions.
    FitnessOptions fitnessOptions = getFitnessSignInOptions();
    GoogleSignInAccount account = getAccount();

    if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {
      saveCall(call);
      GoogleSignIn.requestPermissions(
        getActivity(),
        GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
        account,
        fitnessOptions);
    } else {
      subscribe();
    }
  }

  @Override
  protected void handleOnActivityResult(int requestCode, int resultCode, Intent data) {
    super.handleOnActivityResult(requestCode, resultCode, data);

    PluginCall savedCall = getSavedCall();

    if (savedCall == null) {
      return;
    }
    if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST_CODE) {
      subscribe();
    }
  }

  private void subscribe() {
    GoogleSignInAccount account = getAccount();
    if (account != null) {
      Fitness.getRecordingClient(getActivity(), account).subscribe(DataType.AGGREGATE_STEP_COUNT_DELTA)
        .addOnSuccessListener(new OnSuccessListener<Void>() {
          @Override
          public void onSuccess(Void aVoid) {
          }
        });
      Fitness.getRecordingClient(getActivity(), account).subscribe(DataType.TYPE_ACTIVITY_SEGMENT)
        .addOnSuccessListener(new OnSuccessListener<Void>() {
          @Override
          public void onSuccess(Void aVoid) {
          }
        });
    } else {
    }
  }

  @PluginMethod()
  public void getAccountData(final PluginCall call) {
    final GoogleSignInAccount account = getAccount();
    if (account != null) {
      getWeightAndHeight().addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
        @Override
        public void onComplete(@NonNull Task<DataReadResponse> task) {
          String personId = account.getId();
          String displayName = account.getDisplayName();
          String givenName = account.getGivenName();
          String familyName = account.getFamilyName();
          String email = account.getEmail();
          JSObject result = new JSObject();

          result.put("id", personId);
          result.put("displayName", displayName);
          result.put("givenName", givenName);
          result.put("familyName", familyName);
          result.put("email", email);
          result.put("weight", weight);
          result.put("height", height);
          call.resolve(result);
        }
      });
    } else {
    }
  }

  public Task<DataReadResponse> getWeightAndHeight() {
    GoogleSignInAccount account = getAccount();
    Calendar cal = Calendar.getInstance();
    DataReadRequest readRequest = new DataReadRequest.Builder()
      .read(DataType.TYPE_WEIGHT)
      .read(DataType.TYPE_HEIGHT)
      .setTimeRange(1, cal.getTimeInMillis(), TimeUnit.MILLISECONDS)
      .setLimit(1).build();

    Task<DataReadResponse> dataReadResponseTask = Fitness.getHistoryClient(getActivity(), account)
      .readData(readRequest)
      .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
        @Override
        public void onSuccess(DataReadResponse dataReadResponse) {
          for (DataSet dataSet : dataReadResponse.getDataSets()) {
            showDataSet(dataSet);
          }
        }
      })
      .addOnFailureListener(new OnFailureListener() {
        @Override
        public void onFailure(@NonNull Exception e) {
        }
      });

    return dataReadResponseTask;
  }

  @PluginMethod()
  public void getTodayData(final PluginCall call) {
    GoogleSignInAccount account = getAccount();
    final JSObject result = new JSObject();

    Fitness.getHistoryClient(getActivity(), account).readDailyTotal(DataType.TYPE_STEP_COUNT_DELTA)
      .addOnSuccessListener(new OnSuccessListener<DataSet>() {
        @Override
        public void onSuccess(DataSet dataSet) {
          todayStep = dataSet.isEmpty() ? 0 :dataSet.getDataPoints().get(0).getValue(Field.FIELD_STEPS).asInt();
          result.put("todayStep", todayStep);
        }
      })
      .addOnFailureListener(new OnFailureListener() {
        @Override
        public void onFailure(@NonNull Exception e) {
        }
      });

    Fitness.getHistoryClient(getActivity(), account).readDailyTotal(DataType.TYPE_DISTANCE_DELTA)
      .addOnSuccessListener(new OnSuccessListener<DataSet>() {
        @Override
        public void onSuccess(DataSet dataSet) {
          todayDist = dataSet.isEmpty() ? 0 :(dataSet.getDataPoints().get(0).getValue(Field.FIELD_DISTANCE).asFloat()) / 1000;
          double roundOffTodayDist = Math.round(todayDist * 100.0) / 100.0;
          // result.put("todayDist", roundOffTodayDist);
          result.put("todayDist", todayDist);
        }
      })
      .addOnFailureListener(new OnFailureListener() {
        @Override
        public void onFailure(@NonNull Exception e) {
        }
      });

    Fitness.getHistoryClient(getActivity(), account).readDailyTotal(DataType.TYPE_CALORIES_EXPENDED)
      .addOnSuccessListener(new OnSuccessListener<DataSet>() {
        @Override
        public void onSuccess(DataSet dataSet) {
          todayCal = dataSet.isEmpty() ? 0 : (int) dataSet.getDataPoints().get(0).getValue(Field.FIELD_CALORIES).asFloat();
          result.put("todayCal", todayCal);
          call.resolve(result);
        }
      })
      .addOnFailureListener(new OnFailureListener() {
        @Override
        public void onFailure(@NonNull Exception e) {
        }
      });
  }

  @PluginMethod()
  public void getHistory(final PluginCall call) throws ParseException {
    GoogleSignInAccount account = getAccount();
    String startTime = call.getString("startTime");
    String endTime = call.getString("endTime");

    if(!call.getData().has("startTime")){
      call.reject("Must provide a start time");
      return;
    }

    SimpleDateFormat f = new SimpleDateFormat("EEE MMM d yyyy");
    Date startDate = f.parse(startTime);
    Date endDate = f.parse(endTime);
    long start = startDate.getTime();
    long end = endDate.getTime();

    DataReadRequest readRequest = new DataReadRequest.Builder()
      .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
      .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.TYPE_DISTANCE_DELTA)
      .aggregate(DataType.TYPE_ACTIVITY_SEGMENT, DataType.AGGREGATE_ACTIVITY_SUMMARY)
      .aggregate(DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED)
      .bucketByTime(1, TimeUnit.DAYS)
      .setTimeRange(start, end, TimeUnit.MILLISECONDS)
      .enableServerQueries()
      .build();

    Fitness.getHistoryClient(getActivity(), account).readData(readRequest)
      .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
        @Override
        public void onSuccess(DataReadResponse dataReadResponse) {
          // Used for aggregated data
          if (dataReadResponse.getBuckets().size() > 0) {
            for (Bucket bucket : dataReadResponse.getBuckets()) {
              List<DataSet> dataSets = bucket.getDataSets();
              for (DataSet dataSet : dataSets) {
                showDataSet(dataSet);
              }
            }
            //Used for non-aggregated data
          } else if (dataReadResponse.getDataSets().size() > 0) {
            for (DataSet dataSet : dataReadResponse.getDataSets()) {
              showDataSet(dataSet);
            }
          }
        }
      })
      .addOnFailureListener(new OnFailureListener() {
        @Override
        public void onFailure(@NonNull Exception e) {
        }
      }).addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
      @Override
      public void onComplete(@NonNull Task<DataReadResponse> task) {
        JSObject result = new JSObject();
        result.put("steps", steps);
        result.put("distances", distances);
        result.put("calories", calories);
        call.resolve(result);
      }
    });
  }

  @PluginMethod()
  public void getHistoryActivity(final PluginCall call) throws ParseException{
    GoogleSignInAccount account = getAccount();
    final JSONObject activityObj = new JSONObject();
    String startTime = call.getString("startTime");
    String endTime = call.getString("endTime");

    if(!call.getData().has("startTime")){
      call.reject("Must provide a start time");
      return;
    }

    SimpleDateFormat f = new SimpleDateFormat("EEE MMM d yyyy");
    Date startDate = f.parse(startTime);
    Date endDate = f.parse(endTime);
    long start = startDate.getTime();
    long end = endDate.getTime();

    DataReadRequest readRequest = new DataReadRequest.Builder()
      .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
      .aggregate(DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED)
      .bucketByActivityType( 1, TimeUnit.MINUTES)
      .setTimeRange(start, end, TimeUnit.MILLISECONDS)
      .enableServerQueries()
      .build();

    Fitness.getHistoryClient(getActivity(), account).readData(readRequest)
      .addOnSuccessListener(new OnSuccessListener<DataReadResponse>() {
        @Override
        public void onSuccess(DataReadResponse dataReadResponse) {
          // Each buckets represent activity
          if (dataReadResponse.getBuckets().size() > 0) {
            for (int i = 0; i < dataReadResponse.getBuckets().size(); i++) {
              String activity = dataReadResponse.getBuckets().get(i).getActivity();
              String startTime = getDate(dataReadResponse.getBuckets().get(i).getStartTime(TimeUnit.MILLISECONDS));
              String endTime =  getDate(dataReadResponse.getBuckets().get(i).getEndTime(TimeUnit.MILLISECONDS));

              try {
                JSONObject summary = new JSONObject();
                summary.put("start", startTime);
                summary.put("end", endTime);
                activityObj.put(activity, summary);
              } catch (JSONException e) {
                e.printStackTrace();
              }
            }
          }
        }
      })
      .addOnFailureListener(new OnFailureListener() {
        @Override
        public void onFailure(@NonNull Exception e) {
        }
      })
      .addOnCompleteListener(new OnCompleteListener<DataReadResponse>() {
        @Override
        public void onComplete(@NonNull Task<DataReadResponse> task) {
          JSObject result = new JSObject();
          result.put("activity", activityObj);
          call.resolve(result);
        }
      });
  }

  private String getDate(long dateLong) {
    // convert long to date String
    DateFormat df = new SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss");
    Calendar cal = Calendar.getInstance();
    cal.setTimeInMillis(dateLong);
    return df.format(cal.getTime());
  }

  private void showDataSet(DataSet dataSet) {
    for (DataPoint dp : dataSet.getDataPoints()) {

      for (Field field : dp.getDataType().getFields()) {
        switch (field.getName()) {
          case "weight":
            weight = dp.getValue(Field.FIELD_WEIGHT).asFloat();
            break;
          case "height":
            height = dp.getValue(Field.FIELD_HEIGHT).asFloat() * 100;
            break;
          case "steps":
            steps += dp.getValue(Field.FIELD_STEPS).asInt();
            break;
          case "calories":
            calories += (int) dp.getValue(Field.FIELD_CALORIES).asFloat();
            break;
          case "distances":
            distances += (dp.getValue(Field.FIELD_DISTANCE).asFloat()) / 1000;
            break;
          default:
            break;
        }
      }

    }
  }
}