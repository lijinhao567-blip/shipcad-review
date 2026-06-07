package com.shipcad.review.database;

import org.hibernate.dialect.DmDialect;
import org.hibernate.dialect.sequence.NoSequenceSupport;
import org.hibernate.dialect.sequence.SequenceSupport;

public class ShipCadDmDialect extends DmDialect {
    @Override
    public SequenceSupport getSequenceSupport() {
        return NoSequenceSupport.INSTANCE;
    }

    @Override
    public String getQuerySequencesString() {
        return null;
    }
}
