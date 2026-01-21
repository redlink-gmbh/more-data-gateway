/*
 * Copyright LBI-DHP and/or licensed to LBI-DHP under one or more
 * contributor license agreements (LBI-DHP: Ludwig Boltzmann Institute
 * for Digital Health and Prevention -- A research institute of the
 * Ludwig Boltzmann Gesellschaft, Oesterreichische Vereinigung zur
 * Foerderung der wissenschaftlichen Forschung).
 * Licensed under the Elastic License 2.0.
 */
package io.redlink.more.data.model;

import io.redlink.more.data.api.app.v1.model.ContactInfoDTO;

public record Contact(
        String institute,
        String person,
        String email,
        String phoneNumber
) {
    public ContactInfoDTO toDTO() {
        return new ContactInfoDTO()
                .institute(institute)
                .person(person)
                .email(email)
                .phoneNumber(phoneNumber);
    }
}
