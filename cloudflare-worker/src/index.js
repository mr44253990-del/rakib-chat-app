/**
 * Cloudflare Worker - Firebase Cloud Messaging Push Notification Service
 * For EB Chat Application - Real-time Notifications
 */

export default {
  async fetch(request, env, ctx) {
    // CORS headers
    const corsHeaders = {
      'Access-Control-Allow-Origin': '*',
      'Access-Control-Allow-Methods': 'POST, GET, OPTIONS',
      'Access-Control-Allow-Headers': 'Content-Type, Authorization',
    };

    // Handle CORS preflight
    if (request.method === 'OPTIONS') {
      return new Response(null, {
        status: 204,
        headers: corsHeaders,
      });
    }

    const url = new URL(request.url);
    const pathname = url.pathname;

    try {
      console.log(`[${new Date().toISOString()}] Incoming request: ${request.method} ${pathname}`);

      // Route: POST /send-notification
      if (pathname === '/send-notification' && request.method === 'POST') {
        return await handleSendNotification(request, env, corsHeaders);
      }

      // Route: POST /send-multi-notification
      if (pathname === '/send-multi-notification' && request.method === 'POST') {
        return await handleMultiNotification(request, env, corsHeaders);
      }

      // Route: GET /health
      if (pathname === '/health' && request.method === 'GET') {
        return new Response(
          JSON.stringify({
            status: 'OK',
            service: 'EB Chat - Firebase Push Notification Service',
            timestamp: new Date().toISOString(),
          }),
          {
            status: 200,
            headers: { 'Content-Type': 'application/json', ...corsHeaders },
          }
        );
      }

      // 404
      return new Response(
        JSON.stringify({
          error: 'Endpoint not found',
          path: pathname,
          availableEndpoints: [
            'POST /send-notification',
            'POST /send-multi-notification',
            'GET /health',
          ],
        }),
        {
          status: 404,
          headers: { 'Content-Type': 'application/json', ...corsHeaders },
        }
      );
    } catch (error) {
      console.error('Worker Error:', error);
      return new Response(
        JSON.stringify({
          error: 'Internal Server Error',
          message: error.message,
          timestamp: new Date().toISOString(),
        }),
        {
          status: 500,
          headers: { 'Content-Type': 'application/json', ...corsHeaders },
        }
      );
    }
  },
};

/**
 * Get Firebase Access Token using Service Account
 */
async function getFirebaseAccessToken(env) {
  try {
    const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);

    const header = {
      alg: 'RS256',
      typ: 'JWT',
      kid: serviceAccount.private_key_id,
    };

    const now = Math.floor(Date.now() / 1000);
    const payload = {
      iss: serviceAccount.client_email,
      sub: serviceAccount.client_email,
      aud: 'https://oauth2.googleapis.com/token',
      iat: now,
      exp: now + 3600,
      scope: 'https://www.googleapis.com/auth/firebase.messaging',
    };

    const headerEncoded = base64url(JSON.stringify(header));
    const payloadEncoded = base64url(JSON.stringify(payload));
    const signature = await signJWT(
      `${headerEncoded}.${payloadEncoded}`,
      serviceAccount.private_key
    );

    const jwt = `${headerEncoded}.${payloadEncoded}.${signature}`;

    const tokenResponse = await fetch('https://oauth2.googleapis.com/token', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/x-www-form-urlencoded',
      },
      body: new URLSearchParams({
        grant_type: 'urn:ietf:params:oauth:grant-type:jwt-bearer',
        assertion: jwt,
      }).toString(),
    });

    if (!tokenResponse.ok) {
      throw new Error(`Token request failed: ${tokenResponse.statusText}`);
    }

    const tokenData = await tokenResponse.json();
    return tokenData.access_token;
  } catch (error) {
    console.error('Error getting Firebase access token:', error);
    throw error;
  }
}

/**
 * Send notification to single device
 */
async function handleSendNotification(request, env, corsHeaders) {
  try {
    const { token, title, body, data, sound = 'default', priority = 'high' } = await request.json();

    // Validation
    if (!token) {
      return new Response(
        JSON.stringify({ error: 'FCM token is required' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    if (!title || !body) {
      return new Response(
        JSON.stringify({ error: 'Title and body are required' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);
    const projectId = serviceAccount.project_id;
    const accessToken = await getFirebaseAccessToken(env);

    const message = {
      message: {
        token: token,
        notification: {
          title: title,
          body: body,
        },
        data: data || {},
        android: {
          priority: priority,
          notification: {
            sound: sound,
            channel_id: 'default_notification_channel',
            click_action: 'FLUTTER_NOTIFICATION_CLICK',
            icon: 'ic_notification',
            color: '#FF6B9D',
          },
        },
        apns: {
          payload: {
            aps: {
              alert: {
                title: title,
                body: body,
              },
              sound: sound,
              badge: 1,
              'mutable-content': 1,
            },
          },
        },
      },
    };

    const response = await fetch(
      `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Authorization: `Bearer ${accessToken}`,
        },
        body: JSON.stringify(message),
      }
    );

    if (!response.ok) {
      const error = await response.json();
      throw new Error(`FCM Error: ${JSON.stringify(error)}`);
    }

    const result = await response.json();

    return new Response(
      JSON.stringify({
        success: true,
        message: 'Notification sent successfully',
        messageId: result.name,
        timestamp: new Date().toISOString(),
      }),
      {
        status: 200,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      }
    );
  } catch (error) {
    console.error('Send Notification Error:', error);
    return new Response(
      JSON.stringify({
        error: 'Failed to send notification',
        message: error.message,
        timestamp: new Date().toISOString(),
      }),
      {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      }
    );
  }
}

/**
 * Send notifications to multiple devices
 */
async function handleMultiNotification(request, env, corsHeaders) {
  try {
    const { tokens, title, body, data, priority = 'high' } = await request.json();

    if (!tokens || !Array.isArray(tokens) || tokens.length === 0) {
      return new Response(
        JSON.stringify({ error: 'tokens array is required and must not be empty' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    if (!title || !body) {
      return new Response(
        JSON.stringify({ error: 'Title and body are required' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    const results = {
      successful: [],
      failed: [],
      total: tokens.length,
    };

    // Send to each token
    for (const token of tokens) {
      try {
        const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);
        const projectId = serviceAccount.project_id;
        const accessToken = await getFirebaseAccessToken(env);

        const message = {
          message: {
            token: token,
            notification: {
              title: title,
              body: body,
            },
            data: data || {},
            android: {
              priority: priority,
              notification: {
                sound: 'default',
                channel_id: 'default_notification_channel',
                click_action: 'FLUTTER_NOTIFICATION_CLICK',
                icon: 'ic_notification',
                color: '#FF6B9D',
              },
            },
          },
        };

        const response = await fetch(
          `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`,
          {
            method: 'POST',
            headers: {
              'Content-Type': 'application/json',
              Authorization: `Bearer ${accessToken}`,
            },
            body: JSON.stringify(message),
          }
        );

        if (response.ok) {
          const result = await response.json();
          results.successful.push({
            token: token,
            messageId: result.name,
          });
        } else {
          const error = await response.json();
          results.failed.push({
            token: token,
            error: error.error?.message || 'Unknown error',
          });
        }
      } catch (error) {
        results.failed.push({
          token: token,
          error: error.message,
        });
      }
    }

    return new Response(
      JSON.stringify({
        success: results.failed.length === 0,
        message: 'Multicast notification processed',
        results: {
          total: results.total,
          successful: results.successful.length,
          failed: results.failed.length,
          details: results,
        },
        timestamp: new Date().toISOString(),
      }),
      {
        status: 200,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      }
    );
  } catch (error) {
    console.error('Multi Notification Error:', error);
    return new Response(
      JSON.stringify({
        error: 'Failed to send multicast notification',
        message: error.message,
        timestamp: new Date().toISOString(),
      }),
      {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      }
    );
  }
}

/**
 * Helper: Base64 URL encode
 */
function base64url(str) {
  return btoa(str).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
}

/**
 * Helper: Sign JWT with private key
 */
async function signJWT(message, privateKey) {
  const keyData = privateKey
    .replace(/-----BEGIN PRIVATE KEY-----/g, '')
    .replace(/-----END PRIVATE KEY-----/g, '')
    .replace(/\n/g, '');

  const binaryString = atob(keyData);
  const bytes = new Uint8Array(binaryString.length);
  for (let i = 0; i < binaryString.length; i++) {
    bytes[i] = binaryString.charCodeAt(i);
  }

  const key = await crypto.subtle.importKey(
    'pkcs8',
    bytes.buffer,
    {
      name: 'RSASSA-PKCS1-v1_5',
      hash: 'SHA-256',
    },
    false,
    ['sign']
  );

  const signature = await crypto.subtle.sign(
    'RSASSA-PKCS1-v1_5',
    key,
    new TextEncoder().encode(message)
  );

  return base64url(String.fromCharCode(...new Uint8Array(signature)));
}
