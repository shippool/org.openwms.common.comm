/*
 * Copyright 2018 Heiko Scherrer
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.openwms.common.comm.osip.upd.tcp;

import org.openwms.common.comm.CommConstants;
import org.openwms.common.comm.CommonMessageFactory;
import org.openwms.common.comm.MessageMismatchException;
import org.openwms.common.comm.app.Driver;
import org.openwms.common.comm.osip.upd.UpdateMessage;
import org.openwms.common.comm.osip.upd.spi.UpdateFieldLengthProvider;
import org.openwms.common.comm.tcp.TelegramDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.GenericMessage;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.util.Map;

import static java.lang.String.format;
import static org.openwms.common.comm.CommHeader.LENGTH_HEADER;
import static org.openwms.common.comm.Payload.DATE_LENGTH;
import static org.openwms.common.comm.Payload.ERROR_CODE_LENGTH;

/**
 * A UpdateTelegramDeserializer deserializes OSIP SYNC telegram String into
 * {@link UpdateMessage}s.
 *
 * @author <a href="mailto:hscherrer@interface21.io">Heiko Scherrer</a>
 * @see UpdateMessage
 */
@Component
class UpdateTelegramDeserializer implements TelegramDeserializer<UpdateMessage> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UpdateTelegramDeserializer.class);
    private static final Logger TELEGRAM_LOGGER = LoggerFactory.getLogger(CommConstants.CORE_INTEGRATION_MESSAGING);
    private final UpdateFieldLengthProvider provider;
    private final Driver driver;

    UpdateTelegramDeserializer(UpdateFieldLengthProvider provider, Driver driver) {
        this.provider = provider;
        this.driver = driver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Message<UpdateMessage> deserialize(String telegram, Map<String, Object> headers) {
        if (TELEGRAM_LOGGER.isDebugEnabled()) {
            TELEGRAM_LOGGER.debug("Incoming: [{}]", telegram);
        }
        if (provider == null) {
            throw new RuntimeException(format("Telegram handling [%s] not supported", UpdateMessage.IDENTIFIER));
        }
        int startPayload = LENGTH_HEADER + forType().length();
        int startActualLocation = startPayload + provider.barcodeLength();
        int startErrorCode = startActualLocation + provider.locationIdLength();
        int startCreateDate = startErrorCode + ERROR_CODE_LENGTH;

        try {
            GenericMessage<UpdateMessage> result =
                new GenericMessage<>(
                    new UpdateMessage.Builder(provider)
                        .withBarcode(telegram.substring(startPayload, startActualLocation))
                        .withActualLocation(telegram.substring(startActualLocation, startErrorCode))
                        .withErrorCode(telegram.substring(startErrorCode, startCreateDate))
                        .withCreateDate(
                            telegram.substring(startCreateDate, startCreateDate + DATE_LENGTH),
                            driver.getOsip().getDatePattern()
                        ).build(),
                        CommonMessageFactory.createHeaders(telegram, headers)
                );

            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Transformed telegram into UpdateMessage message: [{}]", result);
            }
            return result;
        } catch (ParseException e) {
            throw new MessageMismatchException(e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String forType() {
        return UpdateMessage.IDENTIFIER;
    }
}