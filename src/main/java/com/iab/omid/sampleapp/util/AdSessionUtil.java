package com.iab.omid.sampleapp.util;

import android.content.Context;
import android.content.res.Resources;
import android.webkit.WebView;
import androidx.annotation.NonNull;
import com.iab.omid.library.criteo.Omid;
import com.iab.omid.library.criteo.adsession.AdSession;
import com.iab.omid.library.criteo.adsession.AdSessionConfiguration;
import com.iab.omid.library.criteo.adsession.AdSessionContext;
import com.iab.omid.library.criteo.adsession.CreativeType;
import com.iab.omid.library.criteo.adsession.ImpressionType;
import com.iab.omid.library.criteo.adsession.Owner;
import com.iab.omid.library.criteo.adsession.Partner;
import com.iab.omid.library.criteo.adsession.VerificationScriptResource;
import com.iab.omid.sampleapp.BuildConfig;
import com.iab.omid.sampleapp.R;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collections;
import java.util.List;

/**
 * AdSessionUtil
 * 
 */

public final class AdSessionUtil {

    @NonNull
	public static AdSession getNativeAdSession(Context context) throws MalformedURLException {
		Omid.activate(context.getApplicationContext());


		AdSessionConfiguration adSessionConfiguration =
			AdSessionConfiguration.createAdSessionConfiguration(CreativeType.VIDEO,
				ImpressionType.VIEWABLE,
				Owner.NATIVE,
				Owner.NATIVE, false);

		Partner partner = Partner.createPartner(BuildConfig.PARTNER_NAME, BuildConfig.VERSION_NAME);
		final String omidJs = getOmidJs(context);
		List<VerificationScriptResource> verificationScripts = AdSessionUtil.getVerificationScriptResources();
		AdSessionContext adSessionContext = AdSessionContext.createNativeAdSessionContext(partner, omidJs, verificationScripts, null, null);
		AdSession adSession = AdSession.createAdSession(adSessionConfiguration, adSessionContext);
		return adSession;
	}

	@NonNull
	private static List<VerificationScriptResource> getVerificationScriptResources() throws MalformedURLException {
		VerificationScriptResource verificationScriptResource =
				VerificationScriptResource.createVerificationScriptResourceWithParameters(BuildConfig.VENDOR_KEY, getURL(), BuildConfig.VERIFICATION_PARAMETERS);
		return Collections.singletonList(verificationScriptResource);
	}

	@NonNull
	private static URL getURL() throws MalformedURLException {
		return new URL(BuildConfig.VERIFICATION_URL);
	}

	/**
	 * getOmidJs - gets the Omid JS resource as a string
	 * @param context - used to access the JS resource
	 * @return - the Omid JS resource as a string
	 */
	public static String getOmidJs(Context context) {
		Resources res = context.getResources();
		try (InputStream inputStream = res.openRawResource(R.raw.omsdk_v1)) {
			byte[] b = new byte[inputStream.available()];
			final int bytesRead = inputStream.read(b);
			return new String(b, 0, bytesRead, "UTF-8");
		} catch (IOException e) {
			throw new UnsupportedOperationException("Yikes, omid resource not found", e);
		}
	}
}
