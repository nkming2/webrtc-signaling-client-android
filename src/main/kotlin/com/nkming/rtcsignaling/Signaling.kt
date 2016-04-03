package com.nkming.rtcsignaling

import android.util.Log
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONObject
import org.webrtc.SessionDescription

abstract class RtcSignaling()
{
	companion object
	{
		@JvmStatic
		protected fun getSessionDescription(data: JSONObject)
				: SessionDescription
		{
			val type = SessionDescription.Type.fromCanonicalForm(data.getString(
					"type"))
			val sdp = data.getString("sdp")
			return SessionDescription(type, sdp)
		}

		@JvmStatic
		protected fun getJson(sdp: SessionDescription): JSONObject
		{
			var data = JSONObject()
			data.put("type", sdp.type.canonicalForm())
			data.put("sdp", sdp.description)
			return data
		}
	}

	open fun disconnect()
	{
		_socket.disconnect()
	}

	protected abstract val _socket: Socket
}

/**
 * Signal as an initiator
 *
 * @param serverAddr The address of the signaling server
 * @param offer The SDP offer
 * @param onAnswer Called when an answer has been received. The answer SDP
 * object is passed as the arg
 * @param onToken Called when a server token has been received. Token is to be
 * sent to the receiver in order to establish a connection. The token string is
 * passed as the arg
 */
class RtcSignalingInitiator(serverAddr: String, offer: SessionDescription,
		onAnswer: ((SessionDescription) -> Unit)? = null,
		onToken: ((String) -> Unit)? = null)
		: RtcSignaling()
{
	companion object
	{
		private val LOG_TAG = RtcSignalingInitiator::class.java.canonicalName
	}

	protected override val _socket: Socket

	init
	{
		_socket = IO.socket(serverAddr)
		_socket.on(Socket.EVENT_CONNECT,
		{
			Log.d(LOG_TAG, "connect")
			_socket.emit("signaling-new-initiator")
		})

		_socket.on(Socket.EVENT_DISCONNECT,
		{
			Log.d(LOG_TAG, "disconnect")
		})

		_socket.on(Socket.EVENT_ERROR,
		{
			val e = it[0] as Exception?
			Log.d(LOG_TAG, "error", e)
		})

		_socket.on("signaling-new-initiator:token",
		{
			val token = it[0] as String
			Log.d(LOG_TAG, "signaling-new-initiator:token: $token")
			onToken?.invoke(token)
		})

		_socket.on("signaling-new-receiver",
		{
			Log.d(LOG_TAG, "signaling-new-receiver")
			_socket.emit("signaling-send-offer", offer)
		})

		_socket.on("signaling-send-answer",
		{
			Log.d(LOG_TAG, "signaling-send-answer")
			_socket.emit("signaling-ack-answer")
			disconnect()
			val answer = getSessionDescription(it[0] as JSONObject)
			onAnswer?.invoke(answer)
		})
	}
}

/**
 * Signal as an receiver (the one receiving the token)
 *
 * @param serverAddr The address of the signaling server
 * @param token The received token
 * @param onError Called when some error happens, like unrecognized token. A
 * reason string is passed as the arg
 * @param onOffer Called when an offer has been received. The offer SDP object
 * is passed as the arg
 */
class RtcSignalingReceiver(serverAddr: String, token: String,
		onError: ((String) -> Unit)? = null,
		onOffer: ((SessionDescription) -> Unit)? = null)
		: RtcSignaling()
{
	companion object
	{
		private val LOG_TAG = RtcSignalingReceiver::class.java.canonicalName
	}

	protected override val _socket: Socket

	init
	{
		_socket = IO.socket(serverAddr)
		_socket.on(Socket.EVENT_CONNECT,
		{
			Log.d(LOG_TAG, "connect")
			_socket.emit("signaling-new-receiver", token)
		})

		_socket.on(Socket.EVENT_DISCONNECT,
		{
			Log.d(LOG_TAG, "disconnect")
		})

		_socket.on(Socket.EVENT_ERROR,
		{
			val e = it[0] as Exception?
			Log.d(LOG_TAG, "error", e)
		})

		_socket.on("signaling-req-disconnect",
		{
			val reason = it[0] as String
			Log.d(LOG_TAG, "signaling-req-disconnect: $reason")
			_socket.disconnect()
			onError?.invoke(reason)
		})

		_socket.on("signaling-send-offer",
		{
			Log.d(LOG_TAG, "signaling-send-offer")
			val offer = getSessionDescription(it[0] as JSONObject)
			onOffer?.invoke(offer)
		})

		_socket.on("signaling-ack-answer",
		{
			Log.d(LOG_TAG, "signaling-ack-answer")
			_socket.disconnect()
		})

		_socket.connect()
	}

	fun sendAnswer(answer: SessionDescription)
	{
		_socket.emit("signaling-send-answer", getJson(answer))
	}
}
