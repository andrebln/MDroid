package in.co.praveenkumar.mdroid.activity;

import in.co.praveenkumar.R;
import in.co.praveenkumar.mdroid.dialog.LogoutDialog;
import in.co.praveenkumar.mdroid.helper.ApplicationClass;
import in.co.praveenkumar.mdroid.helper.Param;
import in.co.praveenkumar.mdroid.helper.SessionSetting;
import in.co.praveenkumar.mdroid.playgames.GameHelper;
import in.co.praveenkumar.mdroid.service.ScheduleReceiver;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.anjlab.android.iab.v3.BillingProcessor;
import com.anjlab.android.iab.v3.TransactionDetails;
import com.google.android.gms.games.Games;

public class SettingsActivity extends PreferenceActivity implements
		OnPreferenceClickListener, OnPreferenceChangeListener,
		GameHelper.GameHelperListener {
	SessionSetting session;
	BillingProcessor billing;
	SharedPreferences settings;
	GameHelper mGameHelper;

	@SuppressWarnings("deprecation")
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setTitle("Settings");

		// Play games related
		if (mGameHelper == null) {
			getGameHelper();
		}
		mGameHelper.setup(this);

		// Send a tracker
		((ApplicationClass) getApplication())
				.sendScreen(Param.GA_SCREEN_SETTING);

		// Setup billing
		session = new SessionSetting(this);
		settings = PreferenceManager.getDefaultSharedPreferences(this);
		billing = new BillingProcessor(this, Param.BILLING_LICENSE_KEY,
				new BillingProcessor.IBillingHandler() {
					@Override
					public void onProductPurchased(String productId,
							TransactionDetails details) {
						Toast.makeText(getApplicationContext(),
								"You purchased this already!",
								Toast.LENGTH_LONG).show();
					}

					@Override
					public void onBillingError(int errorCode, Throwable error) {
						Toast.makeText(getApplicationContext(),
								"Purchase failed! Please try again!",
								Toast.LENGTH_LONG).show();
					}

					@Override
					public void onBillingInitialized() {
					}

					@Override
					public void onPurchaseHistoryRestored() {
					}
				});

		// Set signature & adsPref in prefs to current account value
		SharedPreferences.Editor editor = settings.edit();
		editor.putString("messagingSignature", session.getMessageSignature());
		editor.putBoolean("hideAds", Param.hideAdsForSession);
		editor.commit();

		/*
		 * Note: Inflate xml after setting signature value or the new value
		 * won't reflect in the inflated setting
		 */
		addPreferencesFromResource(R.xml.preferences);

		// Enable donate only preferences
		if (isProUser()) {
			findPreference("messagingSignature").setEnabled(true);
			findPreference("notifications").setEnabled(true);
		}

		// Playgames state
		updatePlayLoginState();

		// Add preference click / change listeners
		findPreference("logout").setOnPreferenceClickListener(this);
		findPreference("messagingSignature")
				.setOnPreferenceChangeListener(this);
		findPreference("hideAds").setOnPreferenceChangeListener(this);

		findPreference("notifications").setOnPreferenceChangeListener(this);
		findPreference("notification_frequency").setOnPreferenceChangeListener(
				this);

		findPreference("playgames").setOnPreferenceClickListener(this);
		findPreference("playgames_acheivement").setOnPreferenceClickListener(
				this);
		findPreference("help").setOnPreferenceClickListener(this);
		findPreference("privacyPolicy").setOnPreferenceClickListener(this);
		findPreference("tutorial").setOnPreferenceClickListener(this);

		findPreference("aboutMDroid").setOnPreferenceClickListener(this);
		findPreference("aboutDev").setOnPreferenceClickListener(this);
		findPreference("licenses").setOnPreferenceClickListener(this);
		findPreference("translate").setOnPreferenceClickListener(this);
	}

	@Override
	public boolean onPreferenceClick(Preference preference) {
		String key = preference.getKey();

		if (key.contentEquals("logout")) {
			LogoutDialog lod = new LogoutDialog(this,
					new SessionSetting(this).getCurrentSiteId());
			lod.show();
		}

		if (key.contentEquals("playgames")) {
			if (!mGameHelper.isSignedIn())
				mGameHelper.beginUserInitiatedSignIn();
			else
				mGameHelper.signOut();
			updatePlayLoginState();
		}

		if (key.contentEquals("playgames_acheivement")) {
			if (mGameHelper.getApiClient().isConnected())
				startActivityForResult(
						Games.Achievements.getAchievementsIntent(mGameHelper
								.getApiClient()), 11);
			else
				mGameHelper.beginUserInitiatedSignIn();
		}

		if (key.contentEquals("help")) {
			Intent browserIntent = new Intent(Intent.ACTION_VIEW,
					Uri.parse("http://mdroid.praveenkumar.co.in/#!faq.md"));
			startActivity(browserIntent);
		}

		if (key.contentEquals("privacyPolicy")) {
			Intent browserIntent = new Intent(
					Intent.ACTION_VIEW,
					Uri.parse("http://mdroid.praveenkumar.co.in/#!privacy-policy.md"));
			startActivity(browserIntent);
		}

		if (key.contentEquals("tutorial")) {
			Intent tutorialIntent = new Intent(this, TutorialActivity.class);
			tutorialIntent.putExtra("explicitCall", true);
			this.startActivity(tutorialIntent);
		}

		if (key.contentEquals("aboutMDroid")) {
			Intent browserIntent = new Intent(Intent.ACTION_VIEW,
					Uri.parse("http://mdroid.praveenkumar.co.in"));
			startActivity(browserIntent);
		}

		if (key.contentEquals("aboutDev")) {
			Intent browserIntent = new Intent(Intent.ACTION_VIEW,
					Uri.parse("https://github.com/praveendath92"));
			startActivity(browserIntent);
		}

		if (key.contentEquals("translate")) {
			Intent browserIntent = new Intent(Intent.ACTION_VIEW,
					Uri.parse("https://crowdin.com/project/mdroid"));
			startActivity(browserIntent);
		}

		if (key.contentEquals("licenses")) {
			Intent i = new Intent(this, AppBrowserActivity.class);
			i.putExtra("url", "file:///android_asset/os_licenses.html");
			i.putExtra("title", "Open Source Licences");
			this.startActivity(i);
		}

		return false;
	}

	@Override
	public boolean onPreferenceChange(Preference preference, Object newValue) {
		String key = preference.getKey();

		if (key.contentEquals("hideAds")) {
			int hideCount = session.getAdsHideCount();

			// Deny if he at max hide count already
			if (hideCount >= Param.maxAdsHideCount && !Param.hideAdsForSession) {
				Toast.makeText(
						this,
						"You have hidden ads more than "
								+ Param.maxAdsHideCount + " times already!",
						Toast.LENGTH_LONG).show();
				return false;
			}

			// Send a tracker event
			((ApplicationClass) getApplication()).sendEvent(
					Param.GA_EVENT_CAT_SETTING, Param.GA_EVENT_SETTING_HIDEADS);

			// Increment count only if he is indenting to hide ads
			if (!Param.hideAdsForSession)
				session.setAdsHideCount(++hideCount);

			Param.hideAdsForSession = !Param.hideAdsForSession;
		}

		if (key.contentEquals("notifications")) {
			if (newValue.toString().equals("true"))
				ScheduleReceiver.scheduleService(this);
			else
				ScheduleReceiver.unscheduleService(this);
		}

		if (key.contentEquals("notification_frequency") && isProUser())
			ScheduleReceiver.rescheduleService(this);

		if (key.contentEquals("messagingSignature"))
			session.setMessageSignature(newValue.toString());

		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		if (!billing.handleActivityResult(requestCode, resultCode, data))
			super.onActivityResult(requestCode, resultCode, data);

		// GPG related
		mGameHelper.onActivityResult(requestCode, resultCode, data);
	}

	private Boolean isProUser() {
		return billing.isPurchased(Param.BILLING_DONATION_PID)
				|| billing.isPurchased(Param.BILLING_FEATURE_NOTIFICATIONS_PID)
				|| billing.isPurchased(Param.BILLING_FEATURE_PARTICIPANTS_PID)
				|| billing.isPurchased(Param.BILLING_FEATURE_SEARCH_PID)
				|| billing.isPurchased(Param.BILLING_FEATURE_UPLOADS_PID);
	}

	@Override
	public void onDestroy() {
		if (billing != null)
			billing.release();
		super.onDestroy();
	}

	@Override
	public void onSignInFailed() {
	}

	@Override
	public void onSignInSucceeded() {
		updatePlayLoginState();
	}

	@SuppressLint("NewApi")
	@SuppressWarnings("deprecation")
	void updatePlayLoginState() {
		if (mGameHelper.isSignedIn()) {
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
				findPreference("playgames")
						.setIcon(R.drawable.games_controller);
				findPreference("playgames_acheivement").setIcon(
						R.drawable.games_achievements_green);
			}
			findPreference("playgames").setTitle(
					R.string.activity_settings_playgames_title_disconnect);
		} else {
			if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.HONEYCOMB) {
				findPreference("playgames").setIcon(
						R.drawable.games_controller_grey);
				findPreference("playgames_acheivement").setIcon(
						R.drawable.games_achievements);
			}
			findPreference("playgames").setTitle(
					R.string.activity_settings_playgames_title_connect);
		}
	}

	/**
	 * Google Play Games related
	 */

	public GameHelper getGameHelper() {
		if (mGameHelper == null) {
			mGameHelper = new GameHelper(this, GameHelper.CLIENT_GAMES);
			mGameHelper.enableDebugLog(false);
			mGameHelper.setMaxAutoSignInAttempts(0); // Never AutoSignIn
		}
		return mGameHelper;
	}

	@Override
	protected void onStart() {
		super.onStart();
		mGameHelper.onStart(this);
	}

	@Override
	protected void onStop() {
		super.onStop();
		mGameHelper.onStop();
	}

}
