package org.homio.addon.imou;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.experimental.Accessors;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceDTO;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceDTO.ImouChannel;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceLiveStreamsDTO;
import org.homio.addon.imou.service.ImouDeviceService;
import org.homio.api.Context;
import org.homio.api.ContextMedia.FFMPEGFormat;
import org.homio.api.entity.HasPlace;
import org.homio.api.entity.device.DeviceBaseEntity;
import org.homio.api.entity.device.DeviceEndpointsBehaviourContract;
import org.homio.api.entity.log.HasEntityLog;
import org.homio.api.entity.version.HasFirmwareVersion;
import org.homio.api.entity.video.HasVideoSources;
import org.homio.api.exception.ServerException;
import org.homio.api.model.ActionResponseModel;
import org.homio.api.model.FileContentType;
import org.homio.api.model.FileModel;
import org.homio.api.model.Icon;
import org.homio.api.model.OptionModel;
import org.homio.api.model.device.ConfigDeviceDefinition;
import org.homio.api.model.endpoint.DeviceEndpoint;
import org.homio.api.service.EntityService;
import org.homio.api.ui.UIActionHandler;
import org.homio.api.ui.UISidebarMenu;
import org.homio.api.ui.UISidebarMenu.TopSidebarMenu;
import org.homio.api.ui.field.UIField;
import org.homio.api.ui.field.UIFieldGroup;
import org.homio.api.ui.field.UIFieldSlider;
import org.homio.api.ui.field.action.UIActionButton;
import org.homio.api.ui.field.action.UIContextMenuAction;
import org.homio.api.ui.field.action.v1.UIInputBuilder;
import org.homio.api.ui.field.color.UIFieldColorBgRef;
import org.homio.api.ui.field.condition.UIFieldShowOnCondition;
import org.homio.api.ui.field.image.UIFieldImage;
import org.homio.api.widget.template.WidgetDefinition;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.homio.addon.imou.ImouEntrypoint.IMOU_COLOR;
import static org.homio.addon.imou.ImouEntrypoint.IMOU_ICON;
import static org.homio.addon.imou.service.ImouDeviceService.CONFIG_DEVICE_SERVICE;
import static org.homio.api.ui.field.UIFieldType.HTML;
import static org.homio.api.util.JsonUtils.OBJECT_MAPPER;

@SuppressWarnings({"JpaAttributeTypeInspection", "unused"})
@Getter
@Setter
@Entity
@Accessors(chain = true)
@UISidebarMenu(icon = IMOU_ICON,
  order = 150,
  bg = IMOU_COLOR,
  parent = TopSidebarMenu.DEVICES,
  overridePath = "imou",
  filter = {"*:fas fa-filter:#8DBA73", "status:fas fa-heart-crack:#C452C4"},
  sort = {
    "name~#FF9800:fas fa-arrow-up-a-z:fas fa-arrow-down-z-a",
    "status~#7EAD28:fas fa-turn-up:fas fa-turn-down",
    "place~#9C27B0:fas fa-location-dot:fas fa-location-dot fa-rotate-180"
  })
public final class ImouDeviceEntity extends DeviceBaseEntity
  implements
  DeviceEndpointsBehaviourContract,
  HasFirmwareVersion,
  HasVideoSources,
  HasPlace,
  EntityService<ImouDeviceService>, HasEntityLog {

  public static final String PREFIX = "imou";

  @Override
  public @NotNull String getDeviceFullName() {
    return "%s(%s) [${%s}]".formatted(
      getTitle(),
      getIeeeAddress(),
      defaultIfEmpty(getPlace(), "W.ERROR.PLACE_NOT_SET"));
  }

  @Override
  public @NotNull Map<String, ? extends DeviceEndpoint> getDeviceEndpoints() {
    return optService().map(ImouDeviceService::getEndpoints).orElse(Map.of());
  }

  @UIField(order = 1, hideOnEmpty = true, fullWidth = true, color = "#89AA50", type = HTML, hideInEdit = true)
  @UIFieldShowOnCondition("return !context.get('compactMode')")
  @UIFieldColorBgRef(value = "statusColor", animate = true)
  public String getDescription() {
    String message = getStatusMessage();
    if (message != null && message.contains("Failed to connect")) {
      return "IMOU.CONNECT_ISSUE";
    }
    return null;
  }

  @Override
  @UIField(order = 20, required = true, inlineEditWhenEmpty = true, label = "deviceID")
  @UIFieldShowOnCondition("return !context.get('compactMode')")
  @UIFieldGroup("GENERAL")
  public @Nullable String getIeeeAddress() {
    return super.getIeeeAddress();
  }

  @UIField(order = 25)
  @UIFieldShowOnCondition("return !context.get('compactMode')")
  @UIFieldGroup("GENERAL")
  @UIFieldSlider(min = 30, max = 120)
  public int getFetchDataInterval() {
    return getJsonData("fdi", 60);
  }

  public void setFetchDataInterval(int value) {
    setJsonData("fdi", value);
  }

  @UIField(order = 25)
  @UIFieldShowOnCondition("return !context.get('compactMode')")
  @UIFieldGroup("GENERAL")
  @UIFieldSlider(min = 10, max = 60)
  public int getImageCacheInterval() {
    return getJsonData("ici", 30);
  }

  public void setImageCacheInterval(int value) {
    setJsonData("ici", value);
  }

  @UIField(order = 1, hideOnEmpty = true)
  @UIFieldShowOnCondition("return !context.get('compactMode')")
  @UIFieldGroup(value = "DEVICE", order = 8, borderColor = "#7331AD")
  public String getModel() {
    return super.getModel();
  }

  @UIField(order = 2, hideInEdit = true, disableEdit = true)
  @UIFieldShowOnCondition("return !context.get('compactMode')")
  @UIFieldGroup("DEVICE")
  public boolean getTlsEnable() {
    return getJsonData("tls", false);
  }

  @UIField(order = 3, hideInEdit = true)
  @UIFieldShowOnCondition("return !context.get('compactMode')")
  @UIFieldGroup("DEVICE")
  public String getBrand() {
    return getJsonData("brand");
  }

  @JsonIgnore
  public List<String> getCapabilities() {
    return getJsonDataList("cap");
  }

  @UIField(order = 5, hideInEdit = true)
  @UIFieldShowOnCondition("return !context.get('compactMode')")
  @UIFieldGroup("DEVICE")
  public String getCatalog() {
    return getJsonData("cat");
  }

  @UIField(order = 6, hideOnEmpty = true)
  @UIFieldShowOnCondition("return !context.get('compactMode')")
  @UIFieldGroup("DEVICE")
  public int getChannel() {
    return getJsonData("ch", -1);
  }

  @JsonProperty(access = JsonProperty.Access.READ_ONLY)
  @UIField(order = 500, hideInEdit = true)
  @UIFieldImage
  @UIActionButton(name = "refresh", icon = "fas fa-sync",
    actionHandler = ImouDeviceEntity.UpdateSnapshotActionHandler.class)
  @UIActionButton(name = "get", icon = "fas fa-camera",
    actionHandler = ImouDeviceEntity.GetSnapshotActionHandler.class)
  public byte[] getSnapshot() {
    return optService().map(ImouDeviceService::getSnapshot).orElse(null);
  }

  public void setIcon(String value) {
    setJsonData("icon", value);
  }

  @Override
  public @NotNull List<ConfigDeviceDefinition> findMatchDeviceConfigurations() {
    return optService().map(ImouDeviceService::findDevices).orElse(List.of());
  }

  @Override
  public String getDefaultName() {
    return "Generic Tuya Device";
  }

  @UIContextMenuAction(value = "ALARM_MESSAGES", icon = "fas fa-person-circle-exclamation")
  public ActionResponseModel retrieveAlarmMessages() {
    return ActionResponseModel.showJson("Alarms", getService().getApi().getAlarmMessages(getIeeeAddress()));
  }

  @UIContextMenuAction(value = "GET_LIVE_STREAM_INFO", icon = "fas fa-life-ring")
  public ActionResponseModel retrieveLiveStreamInfo() {
    return ActionResponseModel.showJson("Live streams",
      getService().getApi().getLiveStreamInfo(getIeeeAddress()));
  }

  @SneakyThrows
  public boolean tryUpdateDeviceEntity(ImouDeviceDTO device) {
    long hashCode = getEntityHashCode();
    setName(device.name);
    setIeeeAddress(device.deviceId);
    setJsonData("cat", device.deviceCatalog);
    setJsonData("brand", device.brand);
    setJsonData("tls", device.tlsEnable);
    setJsonData("model", device.deviceModel);
    Set<String> capabilities = Arrays.stream(device.ability.split(",")).collect(Collectors.toSet());
    capabilities.add("MotionDetect");
    if (capabilities.contains("WLM")) {
      capabilities.add("Linkagewhitelight");
    }

    setJsonDataList("cap", capabilities);
    setJsonData("ch", device.channelNum);
    setJsonData("fv", device.version);
    setJsonData("channels", OBJECT_MAPPER.writeValueAsString(device.channels));
    setImageIdentifier(device.deviceModel + ".png");
    return hashCode != getEntityHashCode();
  }

  @Override
  public @Nullable Set<String> getConfigurationErrors() {
    if (getCapabilities().isEmpty()) {
      return Set.of("ERROR.NO_CAPABILITIES");
    }
    return null;
  }

  @Override
  public long getEntityServiceHashCode() {
    return Objects.hashCode(getIeeeAddress()) +
           getJsonDataHashCode("cat", "brand", "fdi", "ici", "tls", "model", "cap", "cat", "ch", "fv", "channels");
  }

  @Override
  public String getFirmwareVersion() {
    return getJsonData("fv");
  }

  public boolean isCompactMode() {
    return context().setting().getValue(ImouEntityCompactModeSetting.class);
  }

  @Override
  public void assembleActions(UIInputBuilder uiInputBuilder) {
    @NotNull List<ConfigDeviceDefinition> configDeviceDefinitions = getService().findDevices();
    List<WidgetDefinition> widgetDefinitions = CONFIG_DEVICE_SERVICE.getDeviceWidgets(configDeviceDefinitions);
    uiInputBuilder.context().widget().createTemplateWidgetActions(uiInputBuilder, this, widgetDefinitions);

    optService().ifPresent(service -> {
      if (getCapabilities().contains("WLAN")) {
        uiInputBuilder.addOpenDialogSelectableButton("NOTIFICATION_CALLBACK_URL", new Icon("fas fa-link", "#6259B8"),
          (context, params) -> {
            String callbackUrl = params.getString("notificationCallbackUrl");

            if (!Objects.equals(callbackUrl, service.getCallbackUrl())) {
              service.updateCallbackUrl(callbackUrl);
            }
            return null;
          }).editDialog(dialogBuilder -> {
          dialogBuilder.setTitle("NOTIFICATION_CALLBACK_URL", new Icon("fas fa-link", "#6259B8"));
          dialogBuilder.addFlex("main", flex ->
            flex.addTextInput("notificationCallbackUrl", service.getCallbackUrl(), false));
        });
      }
    });
  }

  @JsonIgnore
  @SneakyThrows
  public Map<Integer, ImouChannel> getChannels() {
    String channels = getJsonDataRequire("channels", "");
    if (channels.isEmpty()) {
      return Map.of();
    } else {
      List<ImouChannel> o = OBJECT_MAPPER.readValue(channels, new TypeReference<>() {
      });
      return o.stream().collect(Collectors.toMap(ImouChannel::getChannelId, i -> i));
    }
  }

  @Override
  public void logBuilder(EntityLogBuilder entityLogBuilder) {
    entityLogBuilder.addTopicFilterByEntityID("org.homio");
  }

  @Override
  public @NotNull ImouDeviceService createService(@NotNull Context context) {
    return new ImouDeviceService(context, this);
  }

  @Override
  public @NotNull Class<ImouDeviceService> getEntityServiceItemClass() {
    return ImouDeviceService.class;
  }

  @Override
  public @NotNull List<OptionModel> getVideoSources() {
    ImouDeviceLiveStreamsDTO info = getService().getApi().getLiveStreamInfo(getIeeeAddress());
    return info.getStreams().stream().map(s ->
      OptionModel.of(s.getHls(), "Stream %d. %s".formatted(s.getStreamId(), s.getHls()))
        .setIcon(FFMPEGFormat.HLS.getIconModel())).toList();
  }

  @Override
  protected @NotNull String getDevicePrefix() {
    return PREFIX;
  }

  @Override
  public @Nullable String getFallbackImageIdentifier() {
    if (getJsonData().has("icon")) {
      return "https://images.imoucn.com/%s".formatted(getJsonData("icon"));
    }
    return null;
  }

  public static class UpdateSnapshotActionHandler implements UIActionHandler {

    @Override
    public ActionResponseModel handleAction(Context context, JSONObject params) {
      ImouDeviceEntity entity = context.db().getRequire(params.getString("entityID"));
      if (!entity.getStatus().isOnline()) {
        throw new ServerException("W.ERROR.OFFLINE");
      }
      ImouDeviceService service = entity.getService();
      service.takeSnapshot();
      return ActionResponseModel.fired();
    }
  }

  public static class GetSnapshotActionHandler implements UIActionHandler {

    @Override
    public ActionResponseModel handleAction(Context context, JSONObject params) {
      ImouDeviceEntity entity = context.db().getRequire(params.getString("entityID"));
      if (!entity.getStatus().isOnline()) {
        throw new ServerException("W.ERROR.OFFLINE");
      }
      byte[] image = entity.getService().getSnapshot();
      String encodedValue = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image);
      FileModel snapshot = new FileModel("Snapshot", encodedValue, FileContentType.image);
      return ActionResponseModel.showFile(snapshot);
    }
  }
}
