/*
 Copyright 2012-2013 
 Claudio Tesoriero - c.tesoriero-at-baasbox.com

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

 http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */
package com.baasbox.controllers.actions.filters;

import org.apache.commons.lang.StringUtils;

import play.Logger;
import play.mvc.Action;
import play.mvc.Http;
import play.mvc.Http.Context;
import play.mvc.Result;

import com.baasbox.BBConfiguration;
import com.baasbox.controllers.CustomHttpCode;
import com.baasbox.security.SessionKeys;

/**
 * This Filter checks if user credentials are present in the request and injects
 * them into the context. Otherwise, injects the internal user for anonymous
 * access
 * 
 * @author utente
 * 
 */
public class UserOrAnonymousCredentialsFilter extends Action.Simple {

	@Override
	public Result call(Context ctx) throws Throwable {
		Logger.trace("Method Start");
		Result tempResult = null;
		Http.Context.current.set(ctx);
		String token = ctx.request().getHeader(SessionKeys.TOKEN.toString());
		String authHeader = ctx.request().getHeader("authorization");
		String appCode = RequestHeaderHelper.getAppCode(ctx);

		boolean isCredentialOk = false;
		boolean anonymousInjected = false;
		// if there is no credentials (token or auth header) it means that there
		// is an anonymous access, let's check only the presence of the appcode
		if (StringUtils.isEmpty(token) && StringUtils.isEmpty(authHeader)) {
			if (!StringUtils.isEmpty(appCode)) {
				// inject the internal username/password
				ctx.args.put("username", BBConfiguration.getBaasBoxUsername());
				ctx.args.put("password", BBConfiguration.getBaasBoxPassword());
				ctx.args.put("appcode", appCode);
				isCredentialOk = true;
				anonymousInjected = true;
			} else {
				tempResult = badRequest("Missing Session Token, Authorization info and even the AppCode");
			}
		}

		// checks if there is just a basic auth header, but without the appCode
		if (!isCredentialOk) {
			if (!StringUtils.isEmpty(authHeader)
					&& StringUtils.isEmpty(RequestHeaderHelper.getAppCode(ctx))) {
				Logger.debug("There is basic auth header, but the appcode is missing");
				Logger.debug("Invalid App Code, AppCode is empty!");
				tempResult = badRequest("Invalid App Code. AppCode is empty or not set");
			}
		}

		// until now, no error has been found
		if (tempResult == null) {
			if (!isCredentialOk) // means that no aunoymous access has been
									// detected, so let's check pther auth way:
									// session token or basic auth
				if (!StringUtils.isEmpty(token))
					isCredentialOk = (new SessionTokenAccess())
							.setCredential(ctx);
				else
					isCredentialOk = (new BasicAuthAccess()).setCredential(ctx);

			if (!isCredentialOk) { // no way.... no anoymous access, but the
									// supplied credentials aren't valid
				tempResult = CustomHttpCode.SESSION_TOKEN_EXPIRED.getStatus();
			} else // valid credentials have been found
			// internal administrator is not allowed to access via REST
			if (((String) ctx.args.get("username"))
					.equalsIgnoreCase(BBConfiguration.getBaasBoxAdminUsername())
					|| (((String) ctx.args.get("username"))
							.equalsIgnoreCase(BBConfiguration
									.getBaasBoxUsername()) && !anonymousInjected))
				tempResult = forbidden("The user " + ctx.args.get("username")
						+ " cannot access via REST");

			// if everything is ok.....
			// executes the request
			if (tempResult == null)
				tempResult = delegate.call(ctx);
		}

		WrapResponse wr = new WrapResponse();
		Result result = wr.wrap(ctx, tempResult);

		Logger.debug(result.toString());
		Logger.trace("Method End");
		return result;
	}

}
