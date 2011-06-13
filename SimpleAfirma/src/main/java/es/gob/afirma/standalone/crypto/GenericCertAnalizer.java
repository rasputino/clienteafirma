package es.gob.afirma.standalone.crypto;

import java.awt.Toolkit;
import java.security.cert.X509Certificate;
import java.util.logging.Logger;

import es.gob.afirma.misc.AOUtil;

/** Analizador gen&eacute;rico de certificados.
 * @author Carlos Gamuci Mill&aacute;n */
public final class GenericCertAnalizer extends CertificateAnalizer {

    @Override
    public boolean isValidCert(final X509Certificate cert) {
        return true;
    }

    @Override
    public CertificateInfo analizeCert(final X509Certificate cert) {

        final CertificateInfo certInfo = new CertificateInfo(AOUtil.getCN(cert));
        try {
            certInfo.setIcon(Toolkit.getDefaultToolkit().getImage(this.getClass().getResource("/resources/default_cert_ico.png")));
        }
        catch (final Exception e) {
            Logger.getLogger("es.gob.afirma").warning("No se pudo cargar el icono por defecto para los certificados");
        }
        return certInfo;
    }
}
