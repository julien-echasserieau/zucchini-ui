package io.zucchiniui.backend.support.ddd.morphia;

import dev.morphia.Datastore;
import dev.morphia.dao.BasicDAO;
import dev.morphia.query.Query;

import java.util.function.Consumer;

public abstract class MorphiaTypedQueryDAO<T, K, Q> extends BasicDAO<T, K> {

    protected MorphiaTypedQueryDAO(final Datastore ds) {
        super(ds);
    }

    public abstract Query<T> prepareTypedQuery(Consumer<? super Q> preparator);

}
