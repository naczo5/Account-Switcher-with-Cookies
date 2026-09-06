package ru.vidtu.iasfork.msauth;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.lwjgl.Sys;

import com.github.mrebhan.ingameaccountswitcher.MR;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;
import net.minecraft.util.Session;
import the_fireplace.ias.tools.HttpTools;

public class AuthSys {
	private static final Gson gson = new Gson();
	private static volatile HttpServer srv;
	private static final Object START_LOCK = new Object();

	public static Gson gson() {
		return gson;
	}
    public static void start(MSAuthScreen gui) {
    	String done = "<html><body><h1>" + I18n.format("ias.msauth.canclosenow") + "</h1></body></html>";
    	new Thread(() -> {
    		synchronized (START_LOCK) {
     		try {
     			if (srv != null) return;
     			gui.setState("ias.msauth.waiting");
     			if (!HttpTools.ping("http://minecraft.net")) throw new MicrosoftAuthException("No intenet connection");
         		try {
         			srv = HttpServer.create(new InetSocketAddress(59125), 0);
         		} catch (java.net.BindException busy) {
         			throw new MicrosoftAuthException(I18n.format("ias.msauth.error.portBusy"));
         		}
            	srv.createContext("/", new HttpHandler() {
    				public void handle(HttpExchange exchange) throws IOException {
    					try {
    						gui.setState("ias.msauth.gettingtoken");
    						byte[] b = done.getBytes(StandardCharsets.UTF_8);
    						exchange.getResponseHeaders().put("Content-Type", Arrays.asList("text/html; charset=UTF-8"));
    						exchange.sendResponseHeaders(200, b.length);
    						OutputStream os = exchange.getResponseBody();
    						os.write(b);
    						os.flush();
    						os.close();
    						String s = exchange.getRequestURI().getQuery();
    						if (s == null) {
    							gui.error("query=null");
    						} else if (s.startsWith("code=")) {
    							accessTokenStep(s.replace("code=", ""), gui);
    						} else if (s.equals("error=access_denied&error_description=The user has denied access to the scope requested by the client application.")) {
    							gui.error(I18n.format("ias.msauth.error.revoked"));
    						} else {
    							gui.error(s);
    						}
    					} catch (Throwable t) {
    						if (t instanceof MicrosoftAuthException) {
    							gui.error(t.getLocalizedMessage());
    						} else {
    							t.printStackTrace();
    							gui.error("Unexpected error: " + t.toString());
    						}
    					}
    					stop();
    				}
    			});
            	srv.start();
            	Sys.openURL("https://login.live.com/oauth20_authorize.srf" +
                        "?client_id=54fd49e4-2103-4044-9603-2b028c814ec3" +
                        "&response_type=code" +
                        "&scope=XboxLive.signin%20XboxLive.offline_access" +
                        "&redirect_uri=http://localhost:59125" +
                        "&prompt=consent");
        	} catch (Throwable t) {
        		if (t instanceof MicrosoftAuthException) {
					gui.error(t.getLocalizedMessage());
				} else {
					gui.error("Unexpected error: " + t.toString());
					t.printStackTrace();
				}
        		stop();
        	}
    		}
    	}, "Auth Thread").start();
    }
    
    public static void stop() {
    	synchronized (START_LOCK) {
     	try {
     		if (srv != null) {
     			srv.stop(0);
     			srv = null;
     		}
     	} catch (Throwable t) {}
    	}
    }

    private static void accessTokenStep(String code, MSAuthScreen gui) throws Throwable {
    	PostRequest pr = new PostRequest("https://login.live.com/oauth20_token.srf").header("Content-Type", "application/x-www-form-urlencoded");
        Map<Object, Object> data = new HashMap<>();
        data.put("client_id", "54fd49e4-2103-4044-9603-2b028c814ec3");
        data.put("code", code);
        data.put("grant_type", "authorization_code");
        data.put("redirect_uri", "http://localhost:59125");
        data.put("scope", "XboxLive.signin XboxLive.offline_access");
        pr.post(data);
        if (pr.response() != 200) throw new MicrosoftAuthException("accessToken response: " + pr.response());
        JsonObject tokenJson = gson.fromJson(pr.body(), JsonObject.class);
        String accessToken = tokenJson.get("access_token").getAsString();
        String refreshToken = tokenJson.has("refresh_token") ? tokenJson.get("refresh_token").getAsString() : "";
        xblStep(accessToken, refreshToken, gui);
    }

    private static void xblStep(String token, String refreshToken, MSAuthScreen gui) throws Throwable {
    	gui.setState("ias.msauth.auth");
    	PostRequest pr = new PostRequest("https://user.auth.xboxlive.com/user/authenticate").header("Content-Type", "application/json").header("Accept", "application/json");
        HashMap<Object, Object> map = new HashMap<>();
        HashMap<Object, Object> sub = new HashMap<>();
        sub.put("AuthMethod", "RPS");
        sub.put("SiteName", "user.auth.xboxlive.com");
        sub.put("RpsTicket", "d=" + token);
        map.put("Properties", sub);
        map.put("RelyingParty", "http://auth.xboxlive.com");
        map.put("TokenType", "JWT");
        pr.post(gson.toJson(map));
        if (pr.response() != 200) throw new MicrosoftAuthException("xbl response: " + pr.response());
        xstsStep(gson.fromJson(pr.body(), JsonObject.class).get("Token").getAsString(), refreshToken, gui);
    }

    private static void xstsStep(String xbl, String refreshToken, MSAuthScreen gui) throws Throwable {
    	PostRequest pr = new PostRequest("https://xsts.auth.xboxlive.com/xsts/authorize").header("Content-Type", "application/json").header("Accept", "application/json");
        HashMap<Object, Object> map = new HashMap<>();
        HashMap<Object, Object> sub = new HashMap<>();
        sub.put("SandboxId", "RETAIL");
        sub.put("UserTokens", Arrays.asList(xbl));
        map.put("Properties", sub);
        map.put("RelyingParty", "rp://api.minecraftservices.com/");
        map.put("TokenType", "JWT");
        pr.post(gson.toJson(map));
        if (pr.response() == 401) throw new MicrosoftAuthException(I18n.format("ias.msauth.error.noxbox"));
        if (pr.response() != 200) throw new MicrosoftAuthException("xsts response: " + pr.response());
        JsonObject jo = gson.fromJson(pr.body(), JsonObject.class);
        minecraftTokenStep(jo.getAsJsonObject("DisplayClaims").getAsJsonArray("xui").get(0)
        		.getAsJsonObject().get("uhs").getAsString(), jo.get("Token").getAsString(), refreshToken, gui);
    }

    private static void minecraftTokenStep(String xbl, String xsts, String refreshToken, MSAuthScreen gui) throws Throwable {
    	PostRequest pr = new PostRequest("https://api.minecraftservices.com/authentication/login_with_xbox").header("Content-Type", "application/json").header("Accept", "application/json");
        Map<Object, Object> map = new HashMap<Object, Object>();
        map.put("identityToken", "XBL3.0 x=" + xbl + ";" + xsts);
        pr.post(gson.toJson(map));
        if (pr.response() != 200) throw new MicrosoftAuthException("minecraftToken response: " + pr.response());
        minecraftStoreVerify(gson.fromJson(pr.body(), JsonObject.class).get("access_token").getAsString(), refreshToken, gui);
    }

    private static void minecraftStoreVerify(String token, String refreshToken, MSAuthScreen gui) throws Throwable {
    	gui.setState("ias.msauth.verify");
    	GetRequest gr = new GetRequest("https://api.minecraftservices.com/entitlements/mcstore").header("Authorization", "Bearer " + token);
        gr.get();
        if (gr.response() != 200) throw new MicrosoftAuthException("minecraftStore response: " + gr.response());
        if (gson.fromJson(gr.body(), JsonObject.class).getAsJsonArray("items").size() == 0) throw new MicrosoftAuthException(I18n.format("ias.msauth.error.gamenotowned"));
        minecraftProfileVerify(token, refreshToken, gui);
    }

    private static void minecraftProfileVerify(String token, String refreshToken, MSAuthScreen gui) throws Throwable {
    	GetRequest gr = new GetRequest("https://api.minecraftservices.com/minecraft/profile").header("Authorization", "Bearer " + token);
        gr.get();
        if (gr.response() != 200) throw new MicrosoftAuthException("minecraftProfile response: " + gr.response());
        JsonObject jo = gson.fromJson(gr.body(), JsonObject.class);
        String name = (String) jo.get("name").getAsString();
        String uuid = (String) jo.get("id").getAsString();
        Minecraft mc = Minecraft.getMinecraft();
        mc.addScheduledTask(() -> {
        	if (mc.currentScreen != gui) return;
			try {
				MR.setSession(new Session(name, uuid, token, "mojang"));
				the_fireplace.ias.account.ExtendedAccountData data = the_fireplace.ias.account.ExtendedAccountData.cookieSession(name, token, uuid, refreshToken);
				data.premium = the_fireplace.ias.enums.EnumBool.TRUE;
				the_fireplace.ias.account.ExtendedAccountData.replaceOrAddCookieAccount(
						com.github.mrebhan.ingameaccountswitcher.tools.alt.AltDatabase.getInstance(), data);
				com.github.mrebhan.ingameaccountswitcher.tools.Config.save();
			} catch (Exception e) {
				e.printStackTrace();
			}
        	mc.displayGuiScreen(null);
        });
    }
    
    public static class MicrosoftAuthException extends Exception {
    	private static final long serialVersionUID = 1L;
    	public MicrosoftAuthException() {}
		public MicrosoftAuthException(String s) {
			super(s);
		}
    }
}
