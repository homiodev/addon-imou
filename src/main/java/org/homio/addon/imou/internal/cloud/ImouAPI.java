package org.homio.addon.imou.internal.cloud;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.StringUtils;
import org.homio.addon.imou.ImouProjectEntity;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceAlarmMessageDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceCallbackUrlDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceEmptyDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceListDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceLiveBindDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceLiveStreamsDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceNightVisionModeDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceOnlineStatusDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouSDCardDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouSDCardDTO.ImouSDCardStatusDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouTokenDTO;
import org.homio.addon.imou.internal.cloud.dto.ResultResponse;
import org.homio.addon.imou.internal.cloud.dto.ResultResponse.Response;
import org.homio.api.exception.ServerException;
import org.homio.api.model.Status;
import org.homio.hquery.Curl;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

/**
 * Implementation of the Imou OpenApi specification
 */
@Log4j2
@Service
public class ImouAPI {

  public static final @NotNull Gson gson = new Gson();

  @Setter
  @Getter
  private static @Nullable ImouProjectEntity projectEntity;
  private final ReentrantLock lock = new ReentrantLock();
  private @Nullable String token;

  private static @NotNull ImouProjectEntity assertApiReady() {
    ImouProjectEntity entity = projectEntity;
    if (entity == null) {
      throw new ImouApiNotReadyException();
    }
    return entity;
  }

  private static Map<String, Object> paramsInit(Map<String, Object> paramsMap, String appId, String appSecret) {
    Map<String, Object> map = new HashMap<>();
    long time = System.currentTimeMillis() / 1000;
    String nonce = UUID.randomUUID().toString();
    String id = UUID.randomUUID().toString();
    //
    StringBuilder paramString = new StringBuilder();
    List<String> paramList = new ArrayList<>();
    for (String key : paramsMap.keySet()) {
      String param = key + ":" + paramsMap.get(key);
      paramList.add(param);
    }
    String[] params = paramList.toArray(new String[0]);
    Arrays.sort(params);
    for (String param : params) {
      paramString.append(param).append(",");
    }
    paramString.append("time:").append(time).append(",");
    paramString.append("nonce:").append(nonce).append(",");
    paramString.append("appSecret:").append(appSecret);

    String sign = DigestUtils.md5Hex(paramString.toString().trim().getBytes(StandardCharsets.UTF_8));

    Map<String, Object> systemMap = new HashMap<>();
    systemMap.put("ver", "1.0");
    systemMap.put("sign", sign);
    systemMap.put("appId", appId);
    systemMap.put("nonce", nonce);
    systemMap.put("time", time);
    map.put("system", systemMap);
    map.put("params", paramsMap);
    map.put("id", id);
    return map;
  }

  public boolean isConnected() {
    return StringUtils.isNotEmpty(token);
  }

  @SneakyThrows
  public String login() {
    if (token == null) {
      try {
        lock.lock();
        if (token == null) {
          ImouProjectEntity projectEntity = assertApiReady();
          String rawResult = request("accessToken", Map.of());
          Type responseType = TypeToken.getParameterized(ResultResponse.class, ImouTokenDTO.class).getType();
          ResultResponse<ImouTokenDTO> resultResponse = Objects.requireNonNull(gson.fromJson(rawResult, responseType));
          Response<ImouTokenDTO> result = resultResponse.getResult();
          if (result.getCode().equals("0")) {
            token = result.getData().getAccessToken();
            projectEntity.setStatus(Status.ONLINE);
          } else {
            projectEntity.setStatus(Status.ERROR, "Code: %s. Msg: %s".formatted(result.getCode(), result.getMsg()));
            throw new IllegalStateException("Imou get access token failed: %s".formatted(result));
          }
        }
      } finally {
        lock.unlock();
      }
    }
    return token;
  }

  public List<ImouDeviceDTO> getDeviceList(int nextPage) {
    Map<String, Object> params = Map.of(
      "queryRange", "1-100",
      "token", login());
    String response = request("deviceList", params);
    try {
      ImouDeviceListDTO dto = processResponse(response, ImouDeviceListDTO.class);
      return dto.getDevices();
    } catch (Exception ex) {
      if (projectEntity != null) {
        projectEntity.setStatus(Status.ERROR, ex.getMessage());
      }
      throw ex;
    }
  }

  public ImouDeviceOnlineStatusDTO getDeviceStatus(String deviceId) {
    Map<String, Object> params = Map.of(
      "deviceId", deviceId,
      "token", login());
    String response = request("deviceOnline", params);
    return processResponse(response, ImouDeviceOnlineStatusDTO.class);
  }

  public ImouDeviceAlarmMessageDTO getAlarmMessages(String deviceId) {
    Calendar beginTime = Calendar.getInstance();
    beginTime.add(Calendar.DAY_OF_MONTH, -30);
    Calendar endTime = Calendar.getInstance();
    endTime.add(Calendar.DAY_OF_MONTH, 1);

    SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    Map<String, Object> params = Map.of(
      "deviceId", deviceId,
      "count", "10",
      "channelId", "0",
      "beginTime", dateFormat.format(beginTime.getTime()),
      "endTime", dateFormat.format(endTime.getTime()),
      "token", login());
    String response = request("getAlarmMessage", params);
    return processResponse(response, ImouDeviceAlarmMessageDTO.class);

  }

  public ImouDeviceNightVisionModeDTO getNightVisionMode(String deviceID) {
    return request("getNightVisionMode", deviceID, "channelId", "0", ImouDeviceNightVisionModeDTO.class);
  }

  public void restart(String deviceId) {
    request("restartDevice", Map.of("deviceId", deviceId, "token", login()));
  }

  public ImouSDCardStatusDTO getDeviceSDCardStatus(String deviceId) {
    String status = request("deviceSdcardStatus", deviceId, ImouSDCardDTO.class).getStatus();
    if ("normal".equals(status)) {
      return request("deviceStorage", deviceId, ImouSDCardStatusDTO.class);
    }
    throw new RuntimeException(status);
  }

  public ImouDeviceCallbackUrlDTO getMessageCallback(String deviceId) {
    return request("getMessageCallback", deviceId, ImouDeviceCallbackUrlDTO.class);
  }

  public void setDeviceCameraStatus(String deviceId, String endpointEntityID, boolean on) {
    Map<String, Object> params = Map.of(
      "deviceId", deviceId,
      "enableType", endpointEntityID,
      "enable", on,
      "token", login());
    String response = request("setDeviceCameraStatus", params);
    processResponse(response, ImouDeviceEmptyDTO.class);
  }

  public void setMessageCallback(String url) {
    Map<String, Object> params = Map.of(
      "callbackUrl", url,
      "callbackFlag", "alarm,deviceStatus",
      "status", url.isEmpty() ? "off" : "on",
      "token", login());
    String response = request("setMessageCallback", params);
    processResponse(response, ImouDeviceEmptyDTO.class);
  }

  @SneakyThrows
  public byte[] getSnapshot(String deviceId) {
    Map<String, Object> params = Map.of(
      "deviceId", deviceId,
      "channelId", "0",
      "token", login());
    String response = request("setDeviceSnapEnhanced", params);
    DeviceSnapEnhancedDTO dto = processResponse(response, DeviceSnapEnhancedDTO.class);
    Thread.sleep(1500);
    return Curl.download(dto.url).getBytes();
  }

  public ImouDeviceLiveBindDTO createBindDeviceLive(String deviceId, CameraProfile profile) {
    Map<String, Object> params = Map.of(
      "deviceId", deviceId,
      "channelId", "0",
      "streamId", profile.ordinal(),
      "token", login());
    String response = request("bindDeviceLive", params);
    return processResponse(response, ImouDeviceLiveBindDTO.class);
  }

  public ImouDeviceLiveStreamsDTO getLiveStreamInfo(String deviceId) {
    Map<String, Object> params = Map.of(
      "deviceId", deviceId,
      "channelId", "0",
      "token", login());
    String response = request("getLiveStreamInfo", params);
    return processResponse(response, ImouDeviceLiveStreamsDTO.class);
  }

  @SneakyThrows
  private <T> T processResponse(@NotNull String contentString, @NotNull Type type) {
    Type responseType = TypeToken.getParameterized(ResultResponse.class, type).getType();
    ResultResponse<T> resultResponse = Objects.requireNonNull(gson.fromJson(contentString, responseType));
    Response<T> result = resultResponse.getResult();
    if ("0".equals(result.getCode())) {
      return result.getData();
    }
    throw new IllegalStateException("%s:%s".formatted(result.getCode(), result.getMsg()));
  }

  public <T> T request(String path, String deviceId, String key, String value, Class<T> responseType) {
    Map<String, Object> params = Map.of(
      "deviceId", deviceId,
      key, value,
      "token", login());
    String response = request(path, params);
    return processResponse(response, responseType);
  }

  public <T> T request(String path, String deviceId, Class<T> responseType) {
    Map<String, Object> params = Map.of(
      "deviceId", deviceId,
      "token", login());
    String response = request(path, params);
    return processResponse(response, responseType);
  }

  @SneakyThrows
  private String request(String path, Map<String, Object> params) {
    ImouProjectEntity projectEntity = assertApiReady();
    Map<String, Object> map = paramsInit(params, projectEntity.getAppUID(), projectEntity.getAppSecret().asString());
    String json = OBJECT_MAPPER.writeValueAsString(map);
    HttpRequest request = Curl.createPostRequest(projectEntity.getDataCenter().getUrl() + path, json);
    HttpResponse<String> response = HttpClient.newBuilder().build().send(request, BodyHandlers.ofString());
    if (response.statusCode() == 200) {
      return response.body();
    } else {
      throw new ServerException("Request failed " + response.body());
    }
  }

  public enum CameraProfile {
    HD, SD
  }

  public static class ImouApiNotReadyException extends IllegalStateException {

    @Override
    public String getMessage() {
      return "Tuya api not ready yet";
    }
  }

  @Getter
  private static class DeviceSnapEnhancedDTO {

    private String url;
  }
}
