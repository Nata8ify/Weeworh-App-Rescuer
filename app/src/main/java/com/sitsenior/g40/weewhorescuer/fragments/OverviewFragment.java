package com.sitsenior.g40.weewhorescuer.fragments;

import android.app.ProgressDialog;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.GsonBuilder;
import com.sitsenior.g40.weewhorescuer.MainActivity;
import com.sitsenior.g40.weewhorescuer.R;
import com.sitsenior.g40.weewhorescuer.adapters.AccidentListAdapter;
import com.sitsenior.g40.weewhorescuer.cores.AccidentFactory;
import com.sitsenior.g40.weewhorescuer.cores.AccidentResultAsyncTask;
import com.sitsenior.g40.weewhorescuer.cores.Weeworh;
import com.sitsenior.g40.weewhorescuer.models.Accident;
import com.sitsenior.g40.weewhorescuer.models.Profile;
import com.sitsenior.g40.weewhorescuer.utils.WeeworhRestService;

import java.io.IOException;
import java.net.CookieManager;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.JavaNetCookieJar;
import okhttp3.OkHttpClient;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

/**
 * Created by PNattawut on 01-Aug-17.
 */

public class OverviewFragment extends Fragment {
    public static  LinearLayout emptyAccidentResultLayout;
    public static  LinearLayout viewIncidentPanelLayout;
    public static  LinearLayout rootOverviewLayout;
    public static ArrayAdapter accidentListAdapter;
    public static  ListView accidentListView;

    private AccidentResultAsyncTask accResultAsyTask;

    private Handler overviewHandler;
    private Runnable overviewRunnable;
    private Runnable incidentViewandNotifyRunnable;

    public static byte accCodeButtonState;

    private OkHttpClient client;
    private Retrofit retrofit;
    private WeeworhRestService weeworh;

    private ProgressDialog loadingProgressDialog;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        accCodeButtonState = Accident.ACC_CODE_A;
        client = new OkHttpClient.Builder()
                .cookieJar(new JavaNetCookieJar(new CookieManager()))
                .build();
        retrofit = new Retrofit.Builder()
                .client(client)
                .baseUrl(Weeworh.Url.HOST)
                .addConverterFactory(GsonConverterFactory.create(new GsonBuilder().setDateFormat("yyyy-MM-dd").create()))
                .build();
        weeworh = retrofit.create(WeeworhRestService.class);
        loadingProgressDialog = new ProgressDialog(getContext());
        loadingProgressDialog.setMessage(getString(R.string.progressing));
        loadingProgressDialog.setCancelable(false);
        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_overview, container, false);
    }

    private List<Accident> rescuePendingIncidentList;
    @Override
    public void onStart() {
        super.onStart();
        accidentListView = (ListView) getView().findViewById(R.id.listvw_a_acclist);
        emptyAccidentResultLayout = (LinearLayout) getView().findViewById(R.id.linrlout_emptyacc);
        rootOverviewLayout = (LinearLayout) getView().findViewById(R.id.linrout_root_overview);
        viewIncidentPanelLayout = (LinearLayout) getView().findViewById(R.id.linrlout_incident_view_panel);
//        accResultAsyTask = new AccidentResultAsyncTask(Profile.getInsatance(), getContext(), emptyAccidentResultLayout, viewIncidentPanelLayout, accidentListView);
//        accResultAsyTask.execute();
        loadingProgressDialog.show();
        weeworh.getInBoundTodayIncidents(Profile.getInsatance().getUserId()).enqueue(new Callback<List<Accident>>() {
            @Override
            public void onResponse(Call<List<Accident>> call, Response<List<Accident>> response) {
                AccidentFactory.getInstance(response.body());
                Log.d("^^%",AccidentFactory.getInstance(null).filterNonCloseIncident().getRescuePendingIncident().toString());
                OverviewFragment.accidentListAdapter = new AccidentListAdapter(getContext(), R.layout.row_accident, AccidentFactory.getInstance(null).filterNonCloseIncident().getRescuePendingIncident());
                accidentListView.setAdapter(accidentListAdapter);
                executeIncidentUpdating();
                loadingProgressDialog.dismiss();
            }

            @Override
            public void onFailure(Call<List<Accident>> call, Throwable t) {

            }
        });

        setListener();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    public void onDestroy() {
        overviewHandler.removeCallbacks(overviewRunnable);
        super.onDestroy();
    }

    public void setListener() {
        /* accidentListView's Listener */
        accidentListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                TextView textView = (TextView)view.findViewById(R.id.txt_briefaddr);
                Accident accident = AccidentFactory.getInstance(null).getAccidentList().get(position);
                AccidentFactory.setSelectAccident(accident);
                ((NavigatorFragment)getFragmentManager().findFragmentByTag("android:switcher:".concat(String.valueOf(R.id.vwpgr_main)).concat(":".concat(String.valueOf(NavigatorFragment.NAVIGATOR_PAGE))))).viewAccidentDataandLocation(accident);
                MainActivity.mainViewPager.setCurrentItem(NavigatorFragment.NAVIGATOR_PAGE);
            }
        });
    }

    public void executeIncidentUpdating(){
        overviewHandler = new Handler();
        incidentViewandNotifyRunnable = new Runnable() {
            @Override
            public void run() {
                OverviewFragment.accidentListAdapter.clear();
                OverviewFragment.accidentListAdapter.addAll(rescuePendingIncidentList);
                OverviewFragment.accidentListAdapter.notifyDataSetChanged();
                if (rescuePendingIncidentList.isEmpty()) {
                    viewIncidentPanelLayout.setVisibility(View.GONE);
                    emptyAccidentResultLayout.setVisibility(View.VISIBLE);
                } else {
                    viewIncidentPanelLayout.setVisibility(View.VISIBLE);
                    emptyAccidentResultLayout.setVisibility(View.GONE);
                }
            }
        };
        overviewRunnable = new Runnable() {
            @Override
            public void run() {
                weeworh.getInBoundTodayIncidents(Profile.getInsatance().getUserId()).enqueue(new Callback<List<Accident>>() {
                    @Override
                    public void onResponse(Call<List<Accident>> call, Response<List<Accident>> response) {
                        if(rescuePendingIncidentList == null){rescuePendingIncidentList = new ArrayList<Accident>();}
                        List<Accident> accs = AccidentFactory.getInstance(response.body()).update().getRescuePendingIncident();
                        if(accs != null) {
                            rescuePendingIncidentList.clear();
                            rescuePendingIncidentList.addAll(accs);
                        }
                        if(OverviewFragment.accidentListAdapter != null){
                            //TODO : Handler NullPointer
                            try {
                                getActivity().runOnUiThread(incidentViewandNotifyRunnable);
                            } catch (Exception  npex){
                                npex.printStackTrace();
                            }
                        }
                    }

                    @Override
                    public void onFailure(Call<List<Accident>> call, Throwable t) {
                    }
                });
                overviewHandler.postDelayed(this, 3000);
            }
        };
        overviewHandler.post(overviewRunnable);

    }

    public static final int OVERVIEW_PAGE = 0;
}
