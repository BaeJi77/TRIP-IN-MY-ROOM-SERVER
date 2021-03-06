package com.trip.my.room.server.user.service

import com.google.gson.Gson
import com.trip.my.room.server.config.MyKakaoConfigurationProperties
import com.trip.my.room.server.user.dto.KakaoUnlinkUser
import com.trip.my.room.server.user.dto.KakaoUser
import com.trip.my.room.server.user.dto.UserDto
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.http.RequestEntity
import org.springframework.stereotype.Service
import org.springframework.util.CollectionUtils
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestTemplate
import java.net.URI


@Service
class KakaoClientService(private val restTemplate: RestTemplate,
						 private val myKakaoConfigProps: MyKakaoConfigurationProperties) {
	
	// authorize_code -> get access_token, refresh_token from kakao
	fun getUser(authorize_code: String): UserDto.UserJoinIn {
		var myMap = mapOf("Content-type" to listOf("application/x-www-form-urlencoded;charset=utf-8"))
		var values: MultiValueMap<String, String> = CollectionUtils.toMultiValueMap(myMap)
		val headers = HttpHeaders(values)
		
		var query = "?grant_type=${myKakaoConfigProps.grantType}"
		query += "&client_id=${myKakaoConfigProps.clientId}"
		query += "&redirect_uri=${myKakaoConfigProps.redirectUrl}"
		query += "&code=$authorize_code"
		
		val url: URI = URI.create(myKakaoConfigProps.authBaseUrl + query)
		val req = RequestEntity({}, headers, HttpMethod.POST, url)
		val re = restTemplate.exchange(req, String::class.java)
		val kUser = Gson().fromJson(re.body, KakaoUser::class.java)
		return getUserExtraInfo(token_type = kUser.token_type, token = kUser.access_token)
	}
	
	fun getUserExtraInfo(token_type: String, token: String): UserDto.UserJoinIn {
		var myMap = mapOf("Content-type" to listOf("application/x-www-form-urlencoded;charset=utf-8"),
				"Authorization" to listOf("${token_type} ${token}"))
		var values: MultiValueMap<String, String> = CollectionUtils.toMultiValueMap(myMap)
		val headers = HttpHeaders(values)
		
		// https://developers.kakao.com/tool/rest-api/open/get/v2-user-me
		val url: URI = URI.create(myKakaoConfigProps.apiBaseUrl + "/v2/user/me")
		val req = RequestEntity({}, headers, HttpMethod.GET, url)
		val re = restTemplate.exchange(req, Any::class.java)
		var response = re.body as HashMap<String, Any>
		var socialId = response.get("id").toString()
		val kakaoAccount = response.get("kakao_account")
		val properties = response.get("properties")
		var email: String = (kakaoAccount as HashMap<String, Any>).get("email") as String
		var nickname: String = (properties as HashMap<String, Any>).get("nickname") as String
		
		return UserDto.UserJoinIn().apply {
			this.name = nickname
			this.email = email
			this.socialId = socialId
			this.social = "kakao"
		}
	}
	
	fun unlinkWithKakao(user_social_id: String): Boolean {
		var myMap = mapOf("Authorization" to listOf("KakaoAK ${myKakaoConfigProps.appAdminKey}"))
		var values: MultiValueMap<String, String> = CollectionUtils.toMultiValueMap(myMap)
		val headers = HttpHeaders(values)
		
		var query = "?target_id_type=user_id"
		query += "&target_id=${user_social_id}"
		
		val url: URI = URI.create(myKakaoConfigProps.apiBaseUrl + "/v1/user/unlink" + query)
		val req = RequestEntity({}, headers, HttpMethod.POST, url)
		val re = restTemplate.exchange(req, String::class.java)
		var unlinkUser = Gson().fromJson(re.body, KakaoUnlinkUser::class.java)
		if (unlinkUser.id.toString() == user_social_id) {
			return true
		}
		return false
	}
}