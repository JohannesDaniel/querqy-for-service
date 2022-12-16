package querqy.converter.solr.map;

import org.junit.Test;
import querqy.QueryConfig;
import querqy.model.BoostedTerm;
import querqy.model.DisjunctionMaxQuery;
import querqy.model.ExpandedQuery;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static querqy.converter.solr.map.MapConverterTestUtils.bqMap;
import static querqy.converter.solr.map.MapConverterTestUtils.dmqMap;
import static querqy.converter.solr.map.MapConverterTestUtils.termMap;
import static querqy.model.convert.builder.BooleanQueryBuilder.bq;
import static querqy.model.convert.builder.DisjunctionMaxQueryBuilder.dmq;
import static querqy.model.convert.builder.ExpandedQueryBuilder.expanded;
import static querqy.model.convert.builder.MatchAllQueryBuilder.matchall;
import static querqy.model.convert.builder.StringRawQueryBuilder.raw;
import static querqy.model.convert.builder.TermBuilder.term;
import static querqy.model.convert.model.Occur.MUST;

public class QuerqyQueryMapConverterTest {

    @Test
    public void testThat_queryIsParsedProperly_forGivenMatchAllQuery() {
        final QuerqyQueryMapConverter converter = QuerqyQueryMapConverter.builder()
                .queryConfig(
                        QueryConfig.builder()
                                .field("f", 1.0f)
                                .build()
                )
                .converterConfig(MapConverterConfig.defaultConfig())
                .node(matchall().build())
                .parseAsUserQuery(true)
                .build();

        assertThat(converter.convert()).isEqualTo(
                Map.of(
                        "lucene", Map.of(
                                "v", "*:*"
                        )
                )
        );
    }

    @Test
    public void testThat_queryIsParsedProperly_forGivenRawQuery() {
        final QuerqyQueryMapConverter converter = QuerqyQueryMapConverter.builder()
                .queryConfig(
                        QueryConfig.builder()
                                .field("f", 1.0f)
                                .build()
                )
                .converterConfig(MapConverterConfig.defaultConfig())
                .node(raw("type:iphone").build())
                .parseAsUserQuery(true)
                .build();

        assertThat(converter.convert()).isEqualTo(
                "type:iphone"
        );
    }

    @Test
    public void testThat_fieldScoreIsAdjusted_forWeightedTerm() {
        final DisjunctionMaxQuery dmq = dmq(List.of()).build();
        final BoostedTerm boostedTerm = new BoostedTerm(dmq, "iphone", 0.5f);
        dmq.getClauses().add(boostedTerm);

        final QuerqyQueryMapConverter converter = QuerqyQueryMapConverter.builder()
                .queryConfig(
                        QueryConfig.builder()
                                .field("f", 20f)
                                .build()
                )
                .converterConfig(MapConverterConfig.defaultConfig())
                .node(dmq)
                .parseAsUserQuery(true)
                .build();

        assertThat(converter.convert()).isEqualTo(
                dmqMap(
                        termMap("f", "iphone", 10f)
                )
        );
    }

    @Test
    public void testThat_tieIsAddedToDmq_forDmqAndDefinedTie() {
        final QuerqyQueryMapConverter converter = QuerqyQueryMapConverter.builder()
                .queryConfig(
                        QueryConfig.builder()
                                .tie(0.5f)
                                .field("f", 1.0f)
                                .build()
                )
                .converterConfig(MapConverterConfig.defaultConfig())
                .node(dmq("iphone").build())
                .parseAsUserQuery(true)
                .build();

        assertThat(converter.convert()).isEqualTo(
                dmqMap(
                        0.5f,
                        termMap("f", "iphone", 1.0f)
                )
        );
    }

    @Test
    public void testThat_termsAreExpanded_forDmqAndTwoFields() {
        final QuerqyQueryMapConverter converter = QuerqyQueryMapConverter.builder()
                .queryConfig(
                        QueryConfig.builder()
                                .field("brand", 30.0f)
                                .field("type", 50.0f)
                                .build()
                )
                .converterConfig(MapConverterConfig.defaultConfig())
                .node(dmq("iphone").build())
                .parseAsUserQuery(true)
                .build();

        assertThat(converter.convert()).isEqualTo(
                dmqMap(
                        termMap("brand", "iphone", 30.0f),
                        termMap("type", "iphone", 50.0f)
                )
        );
    }

    @Test
    public void testThat_termsAreExpandedWithinEachDmq_forTwoFieldsAndTwoQueryTerms() {
        final ExpandedQuery expandedQuery = expanded(bq("iphone", "12")).build();
        final QuerqyQueryMapConverter converter = QuerqyQueryMapConverter.builder()
                .queryConfig(
                        QueryConfig.builder()
                                .field("brand", 30.0f)
                                .field("type", 50.0f)
                                .build()
                )
                .converterConfig(MapConverterConfig.defaultConfig())
                .node(expandedQuery.getUserQuery())
                .parseAsUserQuery(true)
                .build();

        assertThat(converter.convert()).isEqualTo(
                bqMap(
                        "should",
                        dmqMap(
                                termMap("brand", "iphone", 30.0f),
                                termMap("type", "iphone", 50.0f)
                        ),
                        dmqMap(
                                termMap("brand", "12", 30.0f),
                                termMap("type", "12", 50.0f)
                        )
                )
        );
    }

    @Test
    public void testThat_minimumShouldMatchIsOnlyAddedToRootBq_forQueryWithAdditionalNestedBq() {
        final ExpandedQuery expandedQuery = expanded(
                bq(
                        dmq(
                                term("iphone"),
                                bq(
                                        dmq(
                                                List.of(term("apple")),
                                                MUST,
                                                true
                                        ),
                                        dmq(
                                                List.of(term("smartphone")),
                                                MUST,
                                                true
                                        )
                                )
                        ),
                        dmq("12")
                )
        ).build();


        final QuerqyQueryMapConverter converter = QuerqyQueryMapConverter.builder()
                .queryConfig(
                        QueryConfig.builder()
                                .field("f", 1.0f)
                                .minimumShouldMatch("100%")
                                .build()
                )
                .converterConfig(MapConverterConfig.defaultConfig())
                .node(expandedQuery.getUserQuery())
                .parseAsUserQuery(true)
                .build();

        assertThat(converter.convert()).isEqualTo(
                bqMap(
                        "should",
                        "100%",
                        dmqMap(
                                termMap("f", "iphone", 1.0f),
                                bqMap(
                                        0.5f,
                                        "must",
                                        dmqMap(termMap("f", "apple", 1.0f)),
                                        dmqMap(termMap("f", "smartphone", 1.0f))
                                )
                        ),
                        dmqMap(
                                termMap("f", "12", 1.0f)
                        )
                )
        );
    }

}
