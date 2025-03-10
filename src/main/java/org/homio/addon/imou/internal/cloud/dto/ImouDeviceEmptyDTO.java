package org.homio.addon.imou.internal.cloud.dto;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Getter
@Setter
@ToString
public class ImouDeviceEmptyDTO {

  private List<ImouDeviceDTO> devices;
  private int count;

}
