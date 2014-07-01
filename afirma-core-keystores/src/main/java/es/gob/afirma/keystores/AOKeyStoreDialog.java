package es.gob.afirma.keystores;

import java.security.KeyStore.PrivateKeyEntry;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import es.gob.afirma.core.AOCancelledOperationException;
import es.gob.afirma.core.AOException;
import es.gob.afirma.core.keystores.KeyStoreManager;
import es.gob.afirma.core.keystores.NameCertificateBean;
import es.gob.afirma.core.ui.AOUIFactory;
import es.gob.afirma.core.ui.KeyStoreDialogManager;
import es.gob.afirma.keystores.filters.CertificateFilter;

/**
 * Di&aacute;logo para la selecci&oacute;n de certificados.
 * @author Carlos Gamuci
 */
public class AOKeyStoreDialog implements KeyStoreDialogManager {

	private static final Logger logger = Logger.getLogger("es.gob.afirma"); //$NON-NLS-1$

	private KeyStoreManager ksm;
	private final Object parentComponent;
	private final boolean checkPrivateKeys;
	private final boolean checkValidity;
	private final boolean showExpiredCertificates;
	private final List<CertificateFilter> certFilters;
	private final boolean mandatoryCertificate;

	private PrivateKeyEntry selectedPke = null;
	private String selectedAlias = null;

    /**
     * Crea un dialogo para la selecci&oacute;n de un certificado.
     * @param ksm
     *        Gestor de los almac&eacute;nes de certificados a los que pertenecen los alias.
     *        Debe ser {@code null} si se quiere usar el m&eacute;todo para seleccionar
     *        otra cosa que no sean certificados X.509 (como claves de cifrado)
     * @param parentComponent
     *        Componente grafico sobre el que mostrar los di&aacute;logos.
     * @param checkPrivateKeys
     *        Indica si se debe comprobar que el certificado tiene clave
     *        privada o no, para no mostrar aquellos que carezcan de ella
     * @param checkValidity
     *        Indica si se debe comprobar la validez temporal de un
     *        certificado al ser seleccionado
     * @param showExpiredCertificates
     *        Indica si se deben o no mostrar los certificados caducados o
     *        aun no v&aacute;lidos
     */
    public AOKeyStoreDialog(final AOKeyStoreManager ksm,
    		final Object parentComponent,
    		final boolean checkPrivateKeys,
    		final boolean showExpiredCertificates,
    		final boolean checkValidity) {

		if (ksm == null) {
    		throw new IllegalArgumentException("El almacen de claves no puede ser nulo"); //$NON-NLS-1$
    	}

		this.ksm = ksm;
		this.parentComponent = parentComponent;
		this.checkPrivateKeys = checkPrivateKeys;
		this.checkValidity = checkValidity;
		this.showExpiredCertificates = showExpiredCertificates;
		this.certFilters = null;
		this.mandatoryCertificate = false;
    }

    /**
     * Crea un dialogo para la selecci&oacute;n de un certificado.
     * @param ksm
     *        Gestor de los almac&eacute;nes de certificados entre los que se selecciona.
     * @param parentComponent
     *        Componente grafico sobre el que mostrar los di&aacute;logos.
     * @param checkPrivateKeys
     *        Indica si se debe comprobar que el certificado tiene clave
     *        privada o no, para no mostrar aquellos que carezcan de ella
     * @param checkValidity
     *        Indica si se debe comprobar la validez temporal de un
     *        certificado al ser seleccionado
     * @param showExpiredCertificates
     *        Indica si se deben o no mostrar los certificados caducados o
     *        aun no v&aacute;lidos
     * @param certFilters
     *        Filtros sobre los certificados a mostrar
     * @param mandatoryCertificate
     *        Indica si los certificados disponibles (tras aplicar el
     *        filtro) debe ser solo uno.
     */
	public AOKeyStoreDialog(final AOKeyStoreManager ksm,
			final Object parentComponent,
            final boolean checkPrivateKeys,
            final boolean checkValidity,
            final boolean showExpiredCertificates,
            final List<CertificateFilter> certFilters,
            final boolean mandatoryCertificate) {

		if (ksm == null) {
    		throw new IllegalArgumentException("El almacen de claves no puede ser nulo"); //$NON-NLS-1$
    	}

		this.ksm = ksm;
		this.parentComponent = parentComponent;
		this.checkPrivateKeys = checkPrivateKeys;
		this.checkValidity = checkValidity;
		this.showExpiredCertificates = showExpiredCertificates;
		this.certFilters = certFilters;
		this.mandatoryCertificate = mandatoryCertificate;
	}

	@Override
	public NameCertificateBean[] getNameCertificates() {

    	final Map<String, String> aliassesByFriendlyName =
        		KeyStoreUtilities.getAliasesByFriendlyName(
    				this.ksm.getAliases(),
    				this.ksm,
    				this.checkPrivateKeys,
    				this.showExpiredCertificates,
    				this.certFilters
    			);

    	if (this.mandatoryCertificate && aliassesByFriendlyName.size() == 1) {
    		final String alias = aliassesByFriendlyName.keySet().toArray(new String[1])[0];
    		return new NameCertificateBean[] {
    				new NameCertificateBean(
    						alias,
    						aliassesByFriendlyName.get(alias),
    						this.ksm.getCertificate(alias))
    		};
    	}

    	int i = 0;
    	final NameCertificateBean[] namedCerts =
    			new NameCertificateBean[aliassesByFriendlyName.size()];
    	for (final String certAlias : aliassesByFriendlyName.keySet().toArray(new String[aliassesByFriendlyName.size()])) {
    		namedCerts[i++] = new NameCertificateBean(
    				certAlias,
    				aliassesByFriendlyName.get(certAlias),
    				this.ksm.getCertificate(certAlias));
    	}


		return namedCerts;
	}

	@Override
	public void setKeyStoreManager(final KeyStoreManager ksm) {
		this.ksm = ksm;
	}

	@Override
	public Object getKeyEntry(final String alias) throws AOException {

		try {
			this.selectedPke = this.ksm.getKeyEntry(
					alias,
					this.ksm instanceof AOKeyStoreManager ?
							((AOKeyStoreManager) this.ksm).getType().getCertificatePasswordCallback(this.parentComponent) :
								null);
		}
		catch (final Exception e) {
			logger.severe("No se ha podido extraer la clave del almacen: " + e); //$NON-NLS-1$
			throw new AOException("No se ha podido extraer la clave del almacen", e); //$NON-NLS-1$
		}
		this.selectedAlias = alias;

		return this.selectedPke;
	}

	@Override
	public void show() {
		this.selectedPke = (PrivateKeyEntry) AOUIFactory.showCertificateSelectionDialog(this.parentComponent, this);
		if (this.selectedPke == null) {
			throw new AOCancelledOperationException("No se ha seleccionado certificado"); //$NON-NLS-1$
		}
	}

	@Override
	public String getSelectedAlias() {
		return this.selectedAlias;
	}

	@Override
	public PrivateKeyEntry getSelectedPrivateKeyEntry() {
		return this.selectedPke;
	}


}