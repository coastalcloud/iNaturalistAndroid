package org.inaturalist.android;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.preference.CheckBoxPreference;
import android.support.v7.preference.ListPreference;
import android.support.v7.preference.Preference;
import android.support.v7.preference.PreferenceFragmentCompat;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.Log;
import android.view.View;

import com.facebook.login.LoginManager;

import org.apache.http.util.LangUtils;

public class SettingsFragment extends PreferenceFragmentCompat {
    private static final int REQUEST_CODE_LOGIN = 0x1000;

    private Preference mUsernamePreference;
    private CheckBoxPreference mAutoSyncPreference;
    private ListPreference mLanguagePreference;
    private Preference mNetworkPreference;
    private Preference mContactSupport;
    private Preference mVersion;

    private SharedPreferences mPreferences;
    private ActivityHelper mHelper;
    private SharedPreferences.Editor mPrefEditor;
    private INaturalistApp mApp;

    @Override
    public void onCreatePreferences(Bundle bundle, String s) {
        addPreferencesFromResource(R.xml.settings_preferences);

        getPreferenceManager().setSharedPreferencesName("iNaturalistPreferences");

		if (mApp == null) {
            mApp = (INaturalistApp) getActivity().getApplicationContext();
        }

        mUsernamePreference = getPreferenceManager().findPreference("username");
        mAutoSyncPreference = (CheckBoxPreference) getPreferenceManager().findPreference("auto_sync");
        mLanguagePreference = (ListPreference) getPreferenceManager().findPreference("language");
        mNetworkPreference = (Preference) getPreferenceManager().findPreference("inat_network");
        mContactSupport = (Preference) getPreferenceManager().findPreference("contact_support");
        mVersion = (Preference) getPreferenceManager().findPreference("version");

        mHelper = new ActivityHelper(getActivity());
        mPreferences = getActivity().getSharedPreferences("iNaturalistPreferences", Activity.MODE_PRIVATE);
        mPrefEditor = mPreferences.edit();

        refreshSettings();
    }

    private void refreshSettings() {
        String username = mPreferences.getString("username", null);

        if (username == null) {
            // Signed out
            mUsernamePreference.setTitle(R.string.not_logged_in);
            mUsernamePreference.setSummary(R.string.log_in);
            mUsernamePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    Intent intent = new Intent(getActivity(), OnboardingActivity.class);
                    intent.putExtra(OnboardingActivity.LOGIN, true);

                    startActivityForResult(intent, REQUEST_CODE_LOGIN);
                    return false;
                }
            });

        } else {
            // Signed in
            mUsernamePreference.setTitle(Html.fromHtml(String.format(getString(R.string.logged_in_as_html), username)));
            mUsernamePreference.setSummary(R.string.log_out);

            mUsernamePreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    mHelper.confirm(getString(R.string.signed_out),
                            getString(R.string.alert_sign_out),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    signOut();
                                }
                            },
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    dialogInterface.cancel();
                                }
                            }
                    );
                    return false;
                }
            });

        }

        mAutoSyncPreference.setChecked(mApp.getAutoSync());
        mAutoSyncPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                boolean newValue = mAutoSyncPreference.isChecked();
                mAutoSyncPreference.setChecked(newValue);
                mApp.setAutoSync(newValue);
                return false;
            }
        });


        refreshLanguageSettings();
        mLanguagePreference.setOnPreferenceChangeListener(new Preference.OnPreferenceChangeListener() {
            @Override
            public boolean onPreferenceChange(Preference preference, Object o) {
                int index = mLanguagePreference.findIndexOfValue((String)o);
                String locale = LocaleHelper.SupportedLocales[index];
                mPrefEditor.putString("pref_locale", locale);
                mPrefEditor.commit();
                mApp.applyLocaleSettings();
                mApp.restart();
                getActivity().finish();
                return false;
            }
        });


        mContactSupport.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // Get app version
                try {
                    PackageManager manager = getActivity().getPackageManager();
                    PackageInfo info = manager.getPackageInfo(getActivity().getPackageName(), 0);

                    // Open the email client
                    Intent mailer = new Intent(Intent.ACTION_SEND);
                    mailer.setType("message/rfc822");
                    mailer.putExtra(Intent.EXTRA_EMAIL, new String[]{getString(R.string.inat_support_email_address)});
                    String username = mPreferences.getString("username", null);
                    mailer.putExtra(Intent.EXTRA_SUBJECT, String.format(getString(R.string.inat_support_email_subject), info.versionName, info.versionCode, username == null ? "N/A" : username));
                    startActivity(Intent.createChooser(mailer, getString(R.string.send_email)));
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
                return false;
            }
        });

        // Show app version
        try {
            PackageManager manager = getActivity().getPackageManager();
            PackageInfo info = manager.getPackageInfo(getActivity().getPackageName(), 0);

            mVersion.setSummary(String.format("%s (%d)", info.versionName, info.versionCode));
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        String network = mApp.getInaturalistNetworkMember();
        mNetworkPreference.setSummary(mApp.getStringResourceByName("network_" + network));
        mNetworkPreference.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                Intent intent = new Intent(getActivity(), NetworkSettings.class).setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                startActivity(intent);
                return false;
            }
        });
    }

    private void refreshLanguageSettings() {
        String prefLocale = mPreferences.getString("pref_locale", "");
        String[] supportedLocales = LocaleHelper.SupportedLocales;

        if (prefLocale.equals("")) {
            // Use device locale
            mLanguagePreference.setSummary(R.string.use_device_language_settings);
            mLanguagePreference.setValueIndex(0);
        } else {
            for (int i = 0; i < supportedLocales.length; i++) {
                if (prefLocale.equalsIgnoreCase(supportedLocales[i])) {
                    mLanguagePreference.setSummary(getResources().getStringArray(R.array.language_names)[i]);
                    mLanguagePreference.setValueIndex(i);
                    return;
                }
            }
        }
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        // Add the dividers between the preference items
        super.onViewCreated(view, savedInstanceState);
        RecyclerView recyclerView = (RecyclerView) getView().findViewById(R.id.list);
        recyclerView.addItemDecoration(
                new DividerItemDecorationPreferences(getActivity(), 0, 0));
    }


    private class DividerItemDecorationPreferences extends RecyclerView.ItemDecoration {

        private Drawable mDivider;
        private int paddingLeft = 0;
        private int paddingRight = 0;

        public DividerItemDecorationPreferences(Context context, int paddingLeft, int paddingRight) {
            mDivider = ContextCompat.getDrawable(context, R.drawable.divider_recycler_view);
            this.paddingLeft = paddingLeft;
            this.paddingRight = paddingRight;
        }

        @Override
        public void onDrawOver(Canvas c, RecyclerView parent, RecyclerView.State state) {
            int left = paddingLeft;
            int right = parent.getWidth() - paddingRight;
            int childCount = parent.getChildCount();
            boolean lastIteration = false;
            for (int i = 0; i < childCount; i++) {
                if (i == childCount - 1)
                    lastIteration = true;
                View child = parent.getChildAt(i);
                if (!lastIteration) {
                    RecyclerView.LayoutParams params = (RecyclerView.LayoutParams) child.getLayoutParams();
                    int top = child.getBottom() + params.bottomMargin;
                    int bottom = top + mDivider.getIntrinsicHeight();
                    mDivider.setBounds(left, top, right, bottom);
                    mDivider.draw(c);
                }
            }
        }

    }


    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if ((requestCode == REQUEST_CODE_LOGIN) && (resultCode == Activity.RESULT_OK)) {
            // Refresh login state
            refreshSettings();
        }
    }


    private void signOut() {
        INaturalistService.LoginType loginType = INaturalistService.LoginType.valueOf(mPreferences.getString("login_type", INaturalistService.LoginType.OAUTH_PASSWORD.toString()));

        if (loginType == INaturalistService.LoginType.FACEBOOK) {
            LoginManager.getInstance().logOut();
        }

		mPrefEditor.remove("username");
		mPrefEditor.remove("credentials");
		mPrefEditor.remove("password");
		mPrefEditor.remove("login_type");
        mPrefEditor.remove("last_sync_time");
		mPrefEditor.remove("observation_count");
		mPrefEditor.remove("user_icon_url");
		mPrefEditor.remove("user_bio");
		mPrefEditor.remove("user_full_name");
		mPrefEditor.remove("last_user_details_refresh_time");
		mPrefEditor.commit();

		int count1 = getActivity().getContentResolver().delete(Observation.CONTENT_URI, null, null);
		int count2 = getActivity().getContentResolver().delete(ObservationPhoto.CONTENT_URI, null, null);
        int count3 = getActivity().getContentResolver().delete(ProjectObservation.CONTENT_URI, null, null);
        int count4 = getActivity().getContentResolver().delete(ProjectFieldValue.CONTENT_URI, null, null);

        refreshSettings();
	}
}
