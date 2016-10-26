package net.quedex.api.pgp;

import org.bouncycastle.bcpg.ArmoredInputStream;
import org.bouncycastle.openpgp.PGPException;
import org.bouncycastle.openpgp.PGPSignature;
import org.bouncycastle.openpgp.PGPSignatureList;
import org.bouncycastle.openpgp.jcajce.JcaPGPObjectFactory;
import org.bouncycastle.openpgp.operator.bc.BcPGPContentVerifierBuilderProvider;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;

public final class BcSignatureVerifier
{
    private final BcPublicKey publicKey;

    public BcSignatureVerifier(final BcPublicKey publicKey)
    {
        this.publicKey = checkNotNull(publicKey, "null publicKey");
    }

    public String verifySignature(final String message)
        throws PGPInvalidSignatureException, PGPSignatureVerificationException
    {
        try
        {
            final ArmoredInputStream aIn = new ArmoredInputStream(new ByteArrayInputStream(message.getBytes(StandardCharsets.UTF_8)));

            final ByteArrayOutputStream bOut = new ByteArrayOutputStream();
            int ch;

            while ((ch = aIn.read()) >= 0 && aIn.isClearText())
            {
                bOut.write((byte) ch);
            }

            final JcaPGPObjectFactory pgpFact = new JcaPGPObjectFactory(aIn);
            final PGPSignatureList p3 = (PGPSignatureList) pgpFact.nextObject();
            checkState(p3 != null && p3.size() >= 1, "No signatures");
            final PGPSignature sig = p3.get(0);

            sig.init(new BcPGPContentVerifierBuilderProvider(), publicKey.getSigningKey());

            final ByteArrayOutputStream lineOut = new ByteArrayOutputStream();
            final byte[] content = bOut.toByteArray();
            final InputStream sigIn = new ByteArrayInputStream(content);
            int lookAhead = readInputLine(lineOut, sigIn);

            processLine(sig, lineOut.toByteArray());

            if (lookAhead != -1)
            {
                do
                {
                    lookAhead = readInputLine(lineOut, lookAhead, sigIn);

                    sig.update((byte) '\r');
                    sig.update((byte) '\n');

                    processLine(sig, lineOut.toByteArray());
                } while (lookAhead != -1);
            }

            if (sig.verify())
            {
                return new String(content, StandardCharsets.UTF_8);
            }

            throw new PGPInvalidSignatureException(
                "Invalid signature, received keyId=" + Long.toHexString(sig.getKeyID()).toUpperCase()
            );

        }
        catch (IOException | PGPException e)
        {
            throw new PGPSignatureVerificationException("Error verifying message", e);
        }
    }

    private static void processLine(final PGPSignature sig, final byte[] line)
    {
        final int length = getLengthWithoutWhiteSpace(line);

        if (length > 0)
        {
            sig.update(line, 0, length);
        }
    }

    private static int getLengthWithoutWhiteSpace(final byte[] line)
    {
        int end = line.length - 1;

        while (end >= 0 && isWhiteSpace(line[end]))
        {
            end--;
        }

        return end + 1;
    }

    private static boolean isWhiteSpace(final byte b)
    {
        return b == '\r' || b == '\n' || b == '\t' || b == ' ';
    }

    private static int readInputLine(final ByteArrayOutputStream bOut, final InputStream fIn) throws IOException
    {
        bOut.reset();
        int lookAhead = -1;
        int ch;

        while ((ch = fIn.read()) >= 0)
        {
            bOut.write(ch);

            if (ch == '\r' || ch == '\n')
            {
                lookAhead = readPassedEOL(bOut, ch, fIn);
                break;
            }
        }

        return lookAhead;
    }

    private static int readInputLine(final ByteArrayOutputStream bOut, int lookAhead, final InputStream fIn)
        throws IOException
    {
        bOut.reset();
        int ch = lookAhead;

        do
        {
            bOut.write(ch);

            if (ch == '\r' || ch == '\n')
            {
                lookAhead = readPassedEOL(bOut, ch, fIn);
                break;
            }
        } while ((ch = fIn.read()) >= 0);

        return lookAhead;
    }

    private static int readPassedEOL(final ByteArrayOutputStream bOut, final int lastCh, final InputStream fIn)
        throws IOException
    {
        int lookAhead = fIn.read();

        if (lastCh == '\r' && lookAhead == '\n')
        {
            bOut.write(lookAhead);
            lookAhead = fIn.read();
        }

        return lookAhead;
    }
}
