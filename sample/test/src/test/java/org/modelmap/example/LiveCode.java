package org.modelmap.example;

import static com.datastax.driver.core.DataType.text;
import static com.datastax.driver.core.DataType.timeuuid;
import static com.datastax.driver.core.schemabuilder.SchemaBuilder.Direction.DESC;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.toMap;
import static org.modelmap.sample.field.SampleFieldId.EMAIL;
import static org.modelmap.sample.field.SampleFieldId.EMAILS_PREFERENCES;
import static org.modelmap.sample.field.SampleFieldId.FAVORITE_SITE_NAME_1;
import static org.modelmap.sample.field.SampleFieldId.FAVORITE_SITE_NAME_3;
import static org.modelmap.sample.field.SampleFieldId.FAVORITE_SITE_URL_1;
import static org.modelmap.sample.field.SampleFieldId.FAVORITE_SITE_URL_3;
import static org.modelmap.sample.field.SampleFieldId.LOGIN;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Triple;
import org.modelmap.core.FieldId;
import org.modelmap.core.FieldInfo;
import org.modelmap.core.FieldModel;
import org.modelmap.sample.field.SampleTag;
import org.modelmap.sample.model.Account;
import org.modelmap.sample.model.SampleModel;
import org.modelmap.sample.model.SampleModelWrapper;
import org.modelmap.sample.model.SampleModels;

import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.DataType;
import com.datastax.driver.core.querybuilder.Insert;
import com.datastax.driver.core.querybuilder.QueryBuilder;
import com.datastax.driver.core.schemabuilder.Create;
import com.datastax.driver.core.schemabuilder.SchemaBuilder;
import com.datastax.driver.extras.codecs.jdk8.LocalDateCodec;

public class LiveCode {

    public static void main(String[] args) {
        intro();
        example1();
        example2();
        exemple3();
    }

    private static void intro() {
        SampleModel model = new SampleModel();
        model.setAccount(new Account());
        model.getAccount().setEmail("parisjug@gmail.com");
        System.out.println(model.getAccount().getEmail());

        FieldModel fieldModel = new SampleModelWrapper(model);
        System.out.println(fieldModel.<String> get(EMAIL));

        fieldModel.set(EMAIL, "parisjug.gmail.com");
        System.out.println(fieldModel.<String> get(EMAIL));
    }

    private static void example1() {
        FieldModel model = SampleModels.wrapper();
        System.out.println(model.<String> get(EMAIL));
        model.stream().forEach(System.out::println);
        Map<FieldId, Object> map = model.stream().collect(toMap(Entry::getKey, Entry::getValue));
        System.out.println(map);
        SampleModelWrapper newModel = map.entrySet().stream().collect(SampleModelWrapper.toFieldModel());
        newModel.stream().forEach(System.out::println);
        System.out.println(newModel.getModel().getAccount().getEmail());
    }

    private static void example2() {
        FieldModel model = SampleModels.wrapper();
        Map<FieldId, Object> map = model.stream().collect(toMap(Entry::getKey, Entry::getValue));
        SampleModelWrapper newModel = map.entrySet().stream().filter(e -> e.getKey().hasTag(SampleTag.ACCOUNT))
                        // .filter(e -> e.getKey().hasTag(SampleTag.USER))
                        .collect(SampleModelWrapper.toFieldModel());
        newModel.stream().forEach(System.out::println);
    }

    private static void exemple3() {
        FieldModel model = SampleModels.wrapper();
        Create create = SchemaBuilder.createTable("Field").addClusteringColumn(LOGIN.name(), text())
                        .addPartitionKey("snapshot_id", timeuuid());

        stream(model.getFieldInfos()).filter(f -> f.id() != LOGIN)
                        .forEach(f -> create.addColumn(f.id().name(), cqlType(f)));

        Create.Options createWithOptions = create.withOptions().clusteringOrder(LOGIN.name(), DESC);
        System.out.println(createWithOptions);

        Insert insert = QueryBuilder.insertInto("Field");
        model.stream().forEach(e -> insert.value(e.getKey().name(), e.getValue()));

        System.out.println(insert.getQueryString(codecRegistry()));
    }

    public void exemple4() {
        FieldModel sample_1 = SampleModels.wrapper();
        FieldModel sample_2 = SampleModels.wrapper();

        sample_1.set(FAVORITE_SITE_NAME_3, null);
        sample_1.set(FAVORITE_SITE_URL_3, null);
        sample_2.set(FAVORITE_SITE_NAME_1, "LesFurets.com");
        sample_2.set(FAVORITE_SITE_URL_1, "www.lesfurets.com");
        sample_2.set(EMAILS_PREFERENCES, Collections.emptyList());

        /* stream all key-values pair from both models */
        Stream.concat(sample_1.stream().map(buildRight), sample_2.stream().map(buildLeft))

                        /* merging key-value pair in a map */
                        .collect(Collectors.toMap(Triple::getMiddle, Function.identity(), merge))

                        /* filter to keep only key with 2 differents values */
                        .values().stream().filter(isNotSame)

                        /* print keys with differents values */
                        .forEach(System.out::println);
    }

    private static CodecRegistry codecRegistry() {
        final CodecRegistry registry = new CodecRegistry();
        registry.register(LocalDateCodec.instance);
        return registry;
    }

    private static DataType cqlType(FieldInfo info) {
        if (String.class.equals(info.type()))
            return text();
        else if (Boolean.class.equals(info.type()) || Boolean.TYPE.equals(info.type()))
            return DataType.cboolean();
        else if (Long.class.equals(info.type()) || Long.TYPE.equals(info.type()))
            return DataType.cint();
        else if (LocalDate.class.equals(info.type()))
            return DataType.date();
        else if (Enum.class.isAssignableFrom(info.type()))
            return DataType.set(text());
        else if (Collection.class.isAssignableFrom(info.type()))
            return DataType.set(text());
        throw new IllegalArgumentException("unknown type " + info.type() + " for " + info.id());
    }

    static Function<Entry<FieldId, Object>, Triple<Object, FieldId, Object>> buildLeft = (entry) -> {
        return Triple.of(entry.getValue(), entry.getKey(), null);
    };

    static Function<Entry<FieldId, Object>, Triple<Object, FieldId, Object>> buildRight = (entry) -> {
        return Triple.of(null, entry.getKey(), entry.getValue());
    };

    static Predicate<Triple<Object, FieldId, Object>> isNotSame = (triple) -> {
        return !Objects.equals(triple.getLeft(), triple.getRight());
    };

    static BinaryOperator<Triple<Object, FieldId, Object>> merge = (t1, t2) -> {
        Object left = t1.getLeft() != null ? t1.getLeft() : t2.getLeft();
        Object right = t2.getRight() != null ? t2.getRight() : t1.getRight();
        return Triple.of(left, t1.getMiddle(), right);
    };
}
