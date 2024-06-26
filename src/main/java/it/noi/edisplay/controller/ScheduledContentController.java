// SPDX-FileCopyrightText: NOI Techpark <digital@noi.bz.it>
//
// SPDX-License-Identifier: AGPL-3.0-or-later

package it.noi.edisplay.controller;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.fasterxml.jackson.databind.ObjectMapper;

import it.noi.edisplay.components.NOIDataLoader;
import it.noi.edisplay.dto.DisplayContentDto;
import it.noi.edisplay.dto.ScheduledContentDto;
import it.noi.edisplay.model.Display;
import it.noi.edisplay.model.DisplayContent;
import it.noi.edisplay.model.ScheduledContent;
import it.noi.edisplay.model.Template;
import it.noi.edisplay.repositories.DisplayRepository;
import it.noi.edisplay.repositories.ScheduledContentRepository;
import it.noi.edisplay.repositories.TemplateRepository;
import it.noi.edisplay.storage.FileImportStorageS3;
import it.noi.edisplay.utils.ImageUtil;

@RestController
@RequestMapping("/ScheduledContent")
public class ScheduledContentController {
    @Autowired
    ScheduledContentRepository scheduledContentRepository;

    @Autowired
    DisplayRepository displayRepository;

    @Autowired
    ModelMapper modelMapper;

    @Autowired
    private NOIDataLoader noiDataLoader;

    @Autowired
    private TemplateRepository templateRepository;

    @Autowired
    private FileImportStorageS3 fileImportStorageS3;

    @Autowired
    private ImageUtil imageUtil;

    Logger logger = LoggerFactory.getLogger(ScheduledContentController.class);

    @RequestMapping(value = "/get/{uuid}", method = RequestMethod.GET)
    public ResponseEntity<ScheduledContentDto> getScheduledContent(@PathVariable("uuid") String uuid) {
        ScheduledContent scheduledContent = scheduledContentRepository.findByUuid(uuid);
        if (scheduledContent == null) {
            logger.debug("Scheduled content with uuid: " + uuid + " not found.");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        logger.debug("Get scheduled content with uuid: " + uuid);
        return new ResponseEntity<>(modelMapper.map(scheduledContent, ScheduledContentDto.class), HttpStatus.OK);
    }

    @GetMapping(value = "/get-image/{uuid}")
    public ResponseEntity<byte[]> getScheduledImage(@PathVariable("uuid") String uuid, boolean withTextFields)
            throws IOException {
        ScheduledContent scheduledContent = scheduledContentRepository.findByUuid(uuid);
        if (scheduledContent == null) {
            logger.debug("Scheduled Content with uuid: " + uuid + " not found.");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        if (scheduledContent.getDisplayContent() == null) {
            logger.debug("Scheduled Content with uuid: " + uuid + " has no image.");
            return new ResponseEntity<>(HttpStatus.NOT_FOUND);
        }
        byte[] image = fileImportStorageS3.download(scheduledContent.getDisplayContent().getUuid());

        logger.debug("Get scheduled content image with uuid: " + uuid);
        return new ResponseEntity<>(image, HttpStatus.OK);
    }

    @RequestMapping(value = "/all", method = RequestMethod.GET)
    public ResponseEntity getAllScheduledContents(@RequestParam String displayUuid) {
        Display display = displayRepository.findByUuid(displayUuid);

        if (display == null) {
            logger.debug("Display with uuid: " + displayUuid + " not found.");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        List<ScheduledContentDto> dtoList = noiDataLoader.getAllDisplayEvents(display);

        logger.debug("All scheduled content requested. {}", dtoList.size());
        return new ResponseEntity<>(dtoList, HttpStatus.OK);
    }

    @RequestMapping(value = "/create", method = RequestMethod.POST, consumes = "application/json")
    public ResponseEntity createScheduledContent(@RequestBody ScheduledContentDto scheduledContentDto) {
        ScheduledContent scheduledContent = modelMapper.map(scheduledContentDto, ScheduledContent.class);
        scheduledContent.setDisplay(displayRepository.findByUuid(scheduledContentDto.getDisplayUuid()));
        scheduledContent = scheduledContentRepository.saveAndFlush(scheduledContent);
        logger.debug("Scheduled content with uuid:" + scheduledContent.getUuid() + " created.");
        return new ResponseEntity<>(modelMapper.map(scheduledContent, ScheduledContentDto.class), HttpStatus.CREATED);
    }

    @RequestMapping(value = "/delete/{uuid}", method = RequestMethod.DELETE)
    public ResponseEntity deleteScheduledContent(@PathVariable("uuid") String uuid) {
        ScheduledContent scheduledContent = scheduledContentRepository.findByUuid(uuid);
        if (scheduledContent == null) {
            logger.debug("Delete scheduled content with uuid:" + uuid + " failed.");
            return new ResponseEntity(HttpStatus.BAD_REQUEST);
        }
        scheduledContentRepository.delete(scheduledContent);
        logger.debug("Deleted scheduled content with uuid:" + uuid);
        return new ResponseEntity(HttpStatus.OK);
    }

    @RequestMapping(value = "/update", method = RequestMethod.PUT, consumes = "application/json")
    public ResponseEntity updateScheduledContent(@RequestBody ScheduledContentDto scheduledContentDto) {
        ScheduledContent scheduledContent;
        ScheduledContent existingScheduledContent = null;
        Display display = displayRepository.findByUuid(scheduledContentDto.getDisplayUuid());

        if (scheduledContentDto.getUuid() != null) {

            existingScheduledContent = scheduledContentRepository.findByUuid(scheduledContentDto.getUuid());
            // existingScheduledContent =

        } else {
            existingScheduledContent = scheduledContentRepository.findByDisplayIdAndEventId(display.getId(),
                    scheduledContentDto.getEventId());
        }
        if (existingScheduledContent == null) {
            scheduledContent = modelMapper.map(scheduledContentDto, ScheduledContent.class);
        } else {
            existingScheduledContent.setDisabled(scheduledContentDto.getDisabled());
            existingScheduledContent.setStartDate(scheduledContentDto.getStartDate());
            existingScheduledContent.setEndDate(scheduledContentDto.getEndDate());
            existingScheduledContent.setEventDescription(scheduledContentDto.getEventDescription());
            existingScheduledContent.setSpaceDesc(scheduledContentDto.getSpaceDesc());
            existingScheduledContent.setOverride(scheduledContentDto.getOverride());
            existingScheduledContent.setInclude(scheduledContentDto.getInclude());
            existingScheduledContent.setRoom(scheduledContentDto.getRoom());

            scheduledContent = existingScheduledContent;
        }

        scheduledContent.setDisplay(display);
        scheduledContent = scheduledContentRepository.saveAndFlush(scheduledContent);
        logger.debug("Updated scheduled content with uuid:" + scheduledContent.getUuid());

        if (existingScheduledContent == null) {
            return new ResponseEntity<>(modelMapper.map(scheduledContent, ScheduledContentDto.class),
                    HttpStatus.CREATED);
        } else {
            return new ResponseEntity(HttpStatus.ACCEPTED);
        }
    }

    @PostMapping(value = "/set-new-image/{scheduledContentUuid}", consumes = "multipart/form-data")
    public ResponseEntity<DisplayContentDto> setDisplayContent(
            @PathVariable("scheduledContentUuid") String scheduledContentUuid,
            @RequestParam("displayContentDtoJson") String displayContentDtoJson,
            @RequestParam(value = "image", required = false) MultipartFile image) throws IOException {
        ScheduledContent scheduledContent = scheduledContentRepository.findByUuid(scheduledContentUuid);
        if (scheduledContent == null) {
            logger.debug("Scheduled Content with uuid " + scheduledContentUuid + " was not found.");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }
        DisplayContentDto displayContentDto = new ObjectMapper().readValue(displayContentDtoJson,
                DisplayContentDto.class);

        DisplayContent displayContent = modelMapper.map(displayContentDto, DisplayContent.class);
        boolean displayContentExists = scheduledContent.getDisplayContent() != null;

        if (!displayContentExists) {
            scheduledContent.setDisplayContent(new DisplayContent());
            scheduledContent.getDisplayContent().setScheduledContent(scheduledContent);
        }

        scheduledContent.getDisplayContent().setImageFields(displayContent.getImageFields());
        scheduledContent.getDisplayContent().setImageBase64(displayContent.getImageBase64());

        scheduledContent.getDisplayContent().setImageHash(null);
        if (scheduledContent.getDisplayContent().getImageBase64() != null) {
            BufferedImage bImageFromConvert = null;
            byte[] imageBytes = Base64.getDecoder()
                    .decode(imageUtil.drawImageTextFields(scheduledContent.getDisplayContent().getImageFields(),
                            scheduledContent.getDisplay().getResolution().getWidth(),
                            scheduledContent.getDisplay().getResolution().getHeight()));
            ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
            bImageFromConvert = ImageIO.read(bis);
            String fileKey = scheduledContent.getDisplayContent().getUuid();
            fileImportStorageS3.upload(imageUtil.convertToMonochrome(bImageFromConvert), fileKey);
        }

        ScheduledContent savedScheduledContent = scheduledContentRepository.saveAndFlush(scheduledContent);
        if (displayContentExists) {
            logger.debug("Updated image for Scheduled Content uuid:" + scheduledContentUuid);
            return new ResponseEntity<>(HttpStatus.OK);
        }

        logger.debug("Created image for Scheduled Content uuid:" + scheduledContentUuid);
        return new ResponseEntity<>(modelMapper.map(savedScheduledContent.getDisplayContent(), DisplayContentDto.class),
                HttpStatus.CREATED);
    }

    @PostMapping(value = "/set-template-image/{scheduledContentUuid}")
    public ResponseEntity<DisplayContentDto> setScheduledContentByTemplate(
            @PathVariable("scheduledContentUuid") String scheduledContentUuid, String templateUuid,
            @RequestBody DisplayContentDto displayContentDto) throws IOException {
        ScheduledContent scheduledContent = scheduledContentRepository.findByUuid(scheduledContentUuid);

        if (scheduledContent == null) {
            logger.debug("Scheduled Content with uuid " + scheduledContentUuid + " was not found.");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        Template template = templateRepository.findByUuid(templateUuid);
        if (template == null) {
            logger.debug("Template with uuid " + templateUuid + " was not found.");
            return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
        }

        DisplayContent displayContent = modelMapper.map(displayContentDto, DisplayContent.class);

        boolean displayContentExists = scheduledContent.getDisplayContent() != null;
        if (!displayContentExists) {
            scheduledContent.setDisplayContent(new DisplayContent());
            scheduledContent.getDisplayContent().setScheduledContent(scheduledContent);
        }
        scheduledContent.getDisplayContent().setImageFields(displayContent.getImageFields());//
        // set upload
        scheduledContent.getDisplayContent().setImageBase64(displayContent.getImageBase64());
        Display display = displayRepository.findByUuid(scheduledContent.getDisplay().getUuid());
        display.getDisplayContent().setImageFields(displayContent.getImageFields());
        display.getDisplayContent().setImageBase64(displayContent.getImageBase64());

        if (scheduledContent.getDisplayContent().getImageBase64() != null) {
            BufferedImage bImageFromConvert = null;
            byte[] imageBytes = Base64.getDecoder()
                    .decode(scheduledContent.getDisplayContent().getImageBase64().split(",")[1]);
            ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
            bImageFromConvert = ImageIO.read(bis);

            String fileKey = scheduledContent.getDisplayContent().getUuid();
            fileImportStorageS3.upload(imageUtil.convertToMonochrome(bImageFromConvert), fileKey);
        }

        if (displayContent.getImageFields().size() != 0) {
            scheduledContent.getDisplayContent().setImageFields(displayContent.getImageFields());
        }

        scheduledContent.getDisplayContent().setImageHash(null);

        if (scheduledContent.getDisplay().getRoomCodes().length > 1) {
            scheduledContent.getDisplay().setImageHash(null);
            scheduledContent.getDisplay()
                    .setImageBase64(scheduledContent.getDisplay().getCurrentContentMultiRoomsImage());
            BufferedImage bImageFromConvert = null;
            byte[] imageBytes = Base64.getDecoder()
                    .decode(scheduledContent.getDisplay().getImageBase64().split(",")[1]);
            ByteArrayInputStream bis = new ByteArrayInputStream(imageBytes);
            bImageFromConvert = ImageIO.read(bis);
            String fileKey = scheduledContent.getDisplay().getUuid();
            fileImportStorageS3.upload(imageUtil.convertToMonochrome(bImageFromConvert), fileKey);

            scheduledContent.getDisplay()
                    .setImageBase64(scheduledContent.getDisplay().getCurrentContentMultiRoomsImage());
            scheduledContent.getDisplay().setImageHash(null);

        }
        ScheduledContent savedScheduledContent = scheduledContentRepository.saveAndFlush(scheduledContent);
        if (displayContentExists) {
            logger.debug("Updated image for Scheduled Content uuid:" + scheduledContentUuid);
            return new ResponseEntity<>(HttpStatus.OK);
        }

        logger.debug("Created image for Scheduled Content uuid:" + scheduledContentUuid);
        return new ResponseEntity<>(modelMapper.map(savedScheduledContent.getDisplayContent(), DisplayContentDto.class),
                HttpStatus.CREATED);
    }
}
