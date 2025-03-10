package org.homio.addon.imou.internal.cloud.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class ImouDeviceAlarmMessageDTO {

  private int count;
  private String nextAlarmId;
  private List<Alarm> alarms;

  @Getter
  @Setter
  @ToString
  public static class Alarm {

    private String alarmId;
    private String name;
    private long time;
    private String localDate;
    private String type;
    private String thumbUrl;
    private String deviceId;
    private List<String> picurlArray;
    private String channelId;
    private String token;
  }
}
