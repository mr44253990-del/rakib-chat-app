/**
 * Cloudflare Worker - Firebase Push Notification Service
 * Handles real-time notifications for EB Chat Application
 */

import admin from 'firebase-admin';

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
      // Route: POST /send-notification
      if (pathname === '/send-notification' && request.method === 'POST') {
        return await handleSendNotification(request, env, corsHeaders);
      }

      // Route: POST /subscribe
      if (pathname === '/subscribe' && request.method === 'POST') {
        return await handleSubscribe(request, env, corsHeaders);
      }

      // Route: GET /health
      if (pathname === '/health' && request.method === 'GET') {
        return new Response(JSON.stringify({ status: 'OK', timestamp: new Date().toISOString() }), {
          status: 200,
          headers: { 'Content-Type': 'application/json', ...corsHeaders },
        });
      }

      // Route: POST /send-multi-notification
      if (pathname === '/send-multi-notification' && request.method === 'POST') {
        return await handleMultiNotification(request, env, corsHeaders);
      }

      // 404
      return new Response(
        JSON.stringify({ error: 'Endpoint not found', path: pathname }),
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
 * Initialize Firebase Admin SDK
 */
function initializeFirebase(env) {
  const serviceAccount = JSON.parse(env.FIREBASE_SERVICE_ACCOUNT);

  if (!admin.apps.length) {
    admin.initializeApp({
      credential: admin.credential.cert(serviceAccount),
      projectId: serviceAccount.project_id,
    });
  }

  return admin;
}

/**
 * Handle sending notification to single device
 */
async function handleSendNotification(request, env, corsHeaders) {
  try {
    const { token, title, body, data, badge, sound, priority } = await request.json();

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

    const firebaseAdmin = initializeFirebase(env);
    const messaging = firebaseAdmin.messaging();

    const message = {
      token: token,
      notification: {
        title: title,
        body: body,
      },
      android: {
        priority: priority || 'high',
        notification: {
          sound: sound || 'default',
          channelId: 'default_notification_channel',
          clickAction: 'FLUTTER_NOTIFICATION_CLICK',
          icon: 'ic_notification',
        },
      },
      apns: {
        payload: {
          aps: {
            alert: {
              title: title,
              body: body,
            },
            sound: sound || 'default',
            badge: badge || 1,
          },
        },
      },
      webpush: {
        notification: {
          title: title,
          body: body,
          icon: '/logo-192x192.png',
          badge: '/badge-72x72.png',
        },
      },
    };

    // Add custom data if provided
    if (data && typeof data === 'object') {
      message.data = data;
    }

    const response = await messaging.send(message);

    return new Response(
      JSON.stringify({
        success: true,
        message: 'Notification sent successfully',
        messageId: response,
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
        details: error.code || 'UNKNOWN',
      }),
      {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      }
    );
  }
}

/**
 * Handle sending notifications to multiple devices
 */
async function handleMultiNotification(request, env, corsHeaders) {
  try {
    const { tokens, title, body, data, priority } = await request.json();

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

    const firebaseAdmin = initializeFirebase(env);
    const messaging = firebaseAdmin.messaging();

    const message = {
      notification: {
        title: title,
        body: body,
      },
      android: {
        priority: priority || 'high',
        notification: {
          sound: 'default',
          channelId: 'default_notification_channel',
          clickAction: 'FLUTTER_NOTIFICATION_CLICK',
          icon: 'ic_notification',
        },
      },
      apns: {
        payload: {
          aps: {
            alert: {
              title: title,
              body: body,
            },
            sound: 'default',
            badge: 1,
          },
        },
      },
    };

    if (data && typeof data === 'object') {
      message.data = data;
    }

    // Send to all tokens
    const response = await messaging.sendMulticast(message, tokens);

    return new Response(
      JSON.stringify({
        success: true,
        message: 'Multicast notification processed',
        successCount: response.successCount,
        failureCount: response.failureCount,
        failures: response.responses
          .map((resp, idx) => (!resp.success ? { token: tokens[idx], error: resp.error.message } : null))
          .filter(Boolean),
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
      }),
      {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      }
    );
  }
}

/**
 * Handle FCM token subscription (for database storage)
 */
async function handleSubscribe(request, env, corsHeaders) {
  try {
    const { userId, token, deviceName } = await request.json();

    if (!userId || !token) {
      return new Response(
        JSON.stringify({ error: 'userId and token are required' }),
        { status: 400, headers: { 'Content-Type': 'application/json', ...corsHeaders } }
      );
    }

    // You can store this in a database or KV store
    // For now, we'll just validate the token
    return new Response(
      JSON.stringify({
        success: true,
        message: 'Token subscription recorded',
        data: {
          userId,
          token,
          deviceName,
          timestamp: new Date().toISOString(),
        },
      }),
      {
        status: 200,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      }
    );
  } catch (error) {
    return new Response(
      JSON.stringify({
        error: 'Failed to subscribe',
        message: error.message,
      }),
      {
        status: 500,
        headers: { 'Content-Type': 'application/json', ...corsHeaders },
      }
    );
  }
}
