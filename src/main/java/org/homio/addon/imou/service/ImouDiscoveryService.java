package org.homio.addon.imou.service;

import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;
import org.homio.addon.imou.ImouDeviceEntity;
import org.homio.addon.imou.ImouProjectEntity;
import org.homio.addon.imou.internal.cloud.ImouAPI;
import org.homio.addon.imou.internal.cloud.dto.ImouDeviceDTO;
import org.homio.api.Context;
import org.homio.api.service.discovery.ItemDiscoverySupport;
import org.homio.hquery.ProgressBar;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.homio.api.util.Constants.PRIMARY_DEVICE;

@Log4j2
@Service
public class ImouDiscoveryService implements ItemDiscoverySupport {

  @SneakyThrows
  public static boolean updateImouDeviceEntity(ImouDeviceDTO device, ImouAPI api, ImouDeviceEntity entity) {
        /*List<FactoryInformation> infoList = api.getFactoryInformation(List.of(device.id), entity);
        String deviceMac = infoList.stream()
                                   .filter(fi -> fi.id.equals(device.id))
                                   .findAny()
                                   .map(fi -> fi.mac)
                                   .orElse("")
                                   .replaceAll("(..)(?!$)", "$1:");*/

    boolean updated = entity.tryUpdateDeviceEntity(device);

        /*DeviceSchema schema = api.getDeviceSchema(device.id, entity);
        Map<Integer, SchemaDp> schemaDps = new HashMap<>();
        schema.functions.stream().filter(f -> f.dp_id != 0)
                        .forEach(description -> addUniqueSchemaDp(description, schemaDps).setWritable(true));
        schema.status.stream().filter(f -> f.dp_id != 0)
                     .forEach(description -> addUniqueSchemaDp(description, schemaDps).setReadable(true));
        List<SchemaDp> dps = new ArrayList<>(schemaDps.values());
        filterAndConfigureSchema(dps);

        if (!OBJECT_MAPPER.writeValueAsString(entity.getSchema()).equals(OBJECT_MAPPER.writeValueAsString(dps))) {
            entity.setSchema(dps);
            updated = true;
        }*/
    return updated;
  }

  @Override
  public String getName() {
    return "scan-imou-devices";
  }

  @Override
  public @Nullable DeviceScannerResult scan(@NotNull Context context, @NotNull ProgressBar progressBar) {
    DeviceScannerResult result = new DeviceScannerResult();
    Map<String, ImouDeviceEntity> existedDevices =
      context.db().findAll(ImouDeviceEntity.class)
        .stream()
        .collect(Collectors.toMap(ImouDeviceEntity::getIeeeAddress, t -> t));
    try {
      ImouProjectEntity imouProjectEntity = context.db().getRequire(ImouProjectEntity.class, PRIMARY_DEVICE);
      Consumer<ImouDeviceDTO> deviceHandler = device -> {
        ImouDeviceEntity deviceEntity = existedDevices.getOrDefault(device.deviceId, new ImouDeviceEntity());
        if (updateImouDeviceEntity(device, imouProjectEntity.getService().getApi(), deviceEntity)) {
          context.db().save(deviceEntity);
          result.getNewCount().incrementAndGet();
        } else {
          result.getExistedCount().incrementAndGet();
        }
      };
      processDeviceResponse(List.of(), imouProjectEntity.getService(), 0, deviceHandler);
    } catch (Exception ex) {
      log.error("Error scan imou devices", ex);
      context.ui().toastr().error(ex);
    }

    return result;
  }

  public List<ImouDeviceDTO> getDeviceList(Context context) {
    List<ImouDeviceDTO> list = new ArrayList<>();
    ImouProjectEntity entity = context.db().getRequire(ImouProjectEntity.class, PRIMARY_DEVICE);
    processDeviceResponse(List.of(), entity.getService(), 0, list::add);
    return list;
  }

  @SneakyThrows
  private void processDeviceResponse(
    List<ImouDeviceDTO> deviceList,
    ImouProjectService imouProjectService,
    int page,
    Consumer<ImouDeviceDTO> deviceHandler) {
    for (ImouDeviceDTO device : deviceList) {
      deviceHandler.accept(device);
    }
    if (page == 0 || deviceList.size() == 100) {
      int nextPage = page + 1;
      List<ImouDeviceDTO> nextDeviceList = imouProjectService.getApi().getDeviceList(nextPage);
      processDeviceResponse(nextDeviceList, imouProjectService, nextPage, deviceHandler);
    }
  }

    /*private static void filterAndConfigureSchema(List<SchemaDp> schemaDps) {
        Map<String, SchemaDp> schemaCode2Schema = schemaDps.stream().collect(Collectors.toMap(SchemaDp::getCode, s -> s));
        List<String> endpointSuffixes = List.of("", "_1", "_2");
        List<String> switchEndpoints = List.of("switch_led", "led_switch");
        for (String suffix : endpointSuffixes) {
            for (String endpoint : switchEndpoints) {
                SchemaDp switchEndpoint = schemaCode2Schema.get(endpoint + suffix);
                if (switchEndpoint != null) {
                    // remove switch endpoint if brightness or color is present and add to dp2 instead
                    SchemaDp colourEndpoint = schemaCode2Schema.get("colour_data" + suffix);
                    SchemaDp brightEndpoint = schemaCode2Schema.get("bright_value" + suffix);
                    boolean remove = false;

                    if (colourEndpoint != null) {
                        colourEndpoint.setDp2(switchEndpoint.dp);
                        remove = true;
                    }
                    if (brightEndpoint != null) {
                        brightEndpoint.setDp2(switchEndpoint.dp);
                        remove = true;
                    }

                    if (remove) {
                        schemaCode2Schema.remove(endpoint + suffix);
                    }
                }
            }
        }
    }*/

    /*private static SchemaDp addUniqueSchemaDp(Description description, Map<Integer, SchemaDp> schemaMap) {
        SchemaDp schemaDp = schemaMap.get(description.dp_id);
        if (schemaDp != null) {
            schemaDp.mergeMeta(description.values);
            return schemaDp;
        } else {
            // some devices report the same function code for different dps
            // we add an index only if this is the case
            String originalCode = description.code;
            int index = 1;
            while (schemaMap.values().stream().anyMatch(sdp -> sdp.getCode().equals(description.code))) {
                description.code = originalCode + "_" + index;
            }

            schemaDp = SchemaDp.parse(description);
            schemaMap.put(description.dp_id, schemaDp);
        }
        return schemaDp;
    }*/
}
