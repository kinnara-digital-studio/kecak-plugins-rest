package com.kinnara.kecakplugins.rest;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import org.joget.apps.app.service.AppUtil;
import org.joget.commons.util.SecurityUtil;
import org.joget.commons.util.SetupManager;
import org.joget.directory.model.User;
import org.joget.directory.model.service.ExtDirectoryManager;
import org.joget.workflow.model.service.WorkflowUserManager;
import org.springframework.context.ApplicationContext;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class TokenService {

    protected static final String SECRET = SecurityUtil.decrypt(SetupManager.getSettingValue(SetupManager.MASTER_LOGIN_PASSWORD));
    protected static final String HEADER_STRING = "Authorization";
    protected static final String ISSUER = "KecakWorkflow";
    protected static final String DEVICE_ID = "device_id";
    protected static final String FCM_TOKEN = "fcm_token";
    protected static final long EXPIRATIONTIME = 0; // NOW

    /**
     *
     * @param username Current username
     * @param token Firebase Token
     * @param device_id Current Device ID
     * @return JWT Token
     */
    public static String addAuthentication (String username, String token, String device_id){
        String JWT = Jwts.builder()
                .setSubject(username)
                .claim(DEVICE_ID, device_id)
                .claim(FCM_TOKEN, token)
//                .setExpiration(new Date(System.currentTimeMillis()+EXPIRATIONTIME))
                .signWith(io.jsonwebtoken.SignatureAlgorithm.HS256, SECRET)
                .setIssuer(ISSUER)
                .compact();
        return JWT;
    }
    /**
     *
     * @param request HTTP Request
     * @return Logged-in user based on @param request
     * @throws /Exception
     */
    public static User getAuthentication (HttpServletRequest request) throws RestApiException  {
        String session = request.getHeader(HEADER_STRING);
        if (session != null) {
            try {
                String user = Jwts
                        .parser()
                        .setSigningKey(SECRET)
                        .parseClaimsJws(session.replaceAll("^Bearer ", ""))
                        .getBody().getSubject();

                if (user.equals("")) {
                    throw new RestApiException(HttpServletResponse.SC_UNAUTHORIZED, "user is empty");
                } else {
                    ApplicationContext ac = AppUtil.getApplicationContext();
                    ExtDirectoryManager dm = (ExtDirectoryManager) ac.getBean("directoryManager");
                    WorkflowUserManager wfUserManager = (WorkflowUserManager) AppUtil.getApplicationContext().getBean("workflowUserManager");
                    wfUserManager.setCurrentThreadUser(user);
                    return dm.getUserByUsername(user);
                }
            } catch (JwtException e) {
                throw new RestApiException(HttpServletResponse.SC_UNAUTHORIZED,"invalid token");
            }
        } else {
            // get from web session
//            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
//            if (authentication != null) {
//                String username = authentication.getName();
//                if (!"roleAnonymous".equals(username)) {
//                    ApplicationContext ac = AppUtil.getApplicationContext();
//                    ExtDirectoryManager dm = (ExtDirectoryManager) ac.getBean("directoryManager");
//                    return dm.getUserByUsername(username);
//                }
//            }
            throw new RestApiException(HttpServletResponse.SC_UNAUTHORIZED,"token is not found");
        }
    }

    public static Claims getClaims (HttpServletRequest request) {

        String fcmToken = request.getHeader(HEADER_STRING);
        Claims retClaims = null;

        if (fcmToken != null) {
            Jws<Claims> claims = Jwts.parser()
                    .setSigningKey(SECRET)
                    .parseClaimsJws(fcmToken.replaceAll("^Bearer ", ""));
            retClaims = claims.getBody();
        }
        return retClaims;
    }
}
