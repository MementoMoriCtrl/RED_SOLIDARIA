package pe.redsolidaria.modules.donations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.*;

/** Exercises the integration method's response assertions against synthetic JSON; no MVC or Boot context. */
class DonationQueryControlledTest {
    private DonationQueryMySqlIntegrationTest query(String movements) throws Exception {
        var suite=new DonationQueryMySqlIntegrationTest();
        ReflectionTestUtils.setField(suite,"trackingCode","IT-synthetic");
        suite.mvc=mock(MockMvc.class);
        var actions=mock(ResultActions.class); var result=mock(MvcResult.class);
        var response=new MockHttpServletResponse(); response.setContentType("application/json");
        response.getWriter().write("{\"codigoTrazabilidad\":\"IT-synthetic\",\"estado\":\"REGISTRADA\",\"detalles\":[],\"movimientos\":"+movements+"}");
        when(result.getResponse()).thenReturn(response);
        when(suite.mvc.perform(any(RequestBuilder.class))).thenReturn(actions);
        when(actions.andExpect(any(ResultMatcher.class))).thenAnswer(call -> {
            ((ResultMatcher)call.getArgument(0)).match(result); return actions;
        });
        return suite;
    }
    @Test void realMovementWithPublicFieldsSatisfiesTheQueryAssertions() throws Exception {
        query("[{\"estadoNuevo\":\"REGISTRADA\"}]").readsKnownDonationFromLocalMySqlByTrackingCode();
    }
    @ParameterizedTest @ValueSource(strings={"[]","[{\"responsableNombre\":\"private actor\"}]"})
    void emptyMovementsOrExposedResponsibleFailTheActualQueryAssertions(String movements) throws Exception {
        var suite=query(movements);
        assertThrows(AssertionError.class,suite::readsKnownDonationFromLocalMySqlByTrackingCode);
    }
}
